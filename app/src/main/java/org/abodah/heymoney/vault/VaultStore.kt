package org.abodah.heymoney.vault

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** One password kept in the vault. Only ever held in memory, decrypted. */
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val updatedAtMillis: Long,
)

/** Where the vault stands for the UI. */
sealed interface VaultSession {
    /** No vault was created on this phone yet. */
    data object NoVault : VaultSession

    /** A vault exists; the key is not in memory. */
    data object Locked : VaultSession

    /** The vault is open; entries are decrypted in memory only. */
    data class Unlocked(val entries: List<VaultEntry>) : VaultSession
}

/**
 * The local password vault: everything lives in one SharedPreferences file,
 * and everything sensitive in it is ciphertext.
 *
 * Stored (Base64): the PBKDF2 salt, the verifier blob and the vault blob —
 * the whole entry list serialised to JSON and AES-GCM encrypted with the
 * key derived from the master password (see [VaultCrypto]). The key itself
 * exists only in memory while the vault is unlocked, and is zeroed on
 * [lock]. Optionally, a *copy* of the key wrapped by an Android Keystore
 * key that requires biometric authentication is stored too, so the user
 * can reopen the vault with a fingerprint; the master password always
 * keeps working. No password, key or entry is ever written in clear, and
 * nothing leaves the device: there is no server side to this feature.
 */
@Singleton
class VaultStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs =
        context.getSharedPreferences("heymoney_vault", Context.MODE_PRIVATE)

    private var sessionKey: ByteArray? = null

    private val _session = MutableStateFlow<VaultSession>(
        if (exists()) VaultSession.Locked else VaultSession.NoVault
    )
    val session: StateFlow<VaultSession> = _session

    fun exists(): Boolean = prefs.contains(KEY_SALT)

    val biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    // region Create / unlock / lock

    @Synchronized
    fun createVault(password: CharArray) {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        password.fill(' ')
        prefs.edit()
            .putString(KEY_SALT, key64(salt))
            .putString(KEY_VERIFIER, key64(VaultCrypto.makeVerifier(key)))
            .putString(KEY_BLOB, key64(VaultCrypto.encrypt(key, serialize(emptyList()))))
            .remove(KEY_WRAPPED_KEY)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
        sessionKey = key
        _session.value = VaultSession.Unlocked(emptyList())
    }

    /** True when the password is the vault's master password. */
    @Synchronized
    fun unlock(password: CharArray): Boolean {
        val salt = stored(KEY_SALT) ?: return false
        val key = VaultCrypto.deriveKey(password, salt)
        password.fill(' ')
        return openWithKey(key)
    }

    /** Unlocks with a key recovered outside the password path (biometrics). */
    @Synchronized
    fun unlockWithKey(key: ByteArray): Boolean = openWithKey(key.copyOf())

    private fun openWithKey(key: ByteArray): Boolean {
        val verifier = stored(KEY_VERIFIER)
        val blob = stored(KEY_BLOB)
        if (verifier == null || blob == null || !VaultCrypto.checkVerifier(key, verifier)) {
            key.fill(0)
            return false
        }
        val entries = try {
            deserialize(VaultCrypto.decrypt(key, blob))
        } catch (e: Exception) {
            key.fill(0)
            return false
        }
        sessionKey?.fill(0)
        sessionKey = key
        _session.value = VaultSession.Unlocked(entries)
        return true
    }

    @Synchronized
    fun lock() {
        sessionKey?.fill(0)
        sessionKey = null
        if (exists()) _session.value = VaultSession.Locked
    }

    // endregion

    // region Entries (unlocked only)

    @Synchronized
    fun addEntry(entry: VaultEntry) = mutate { it + entry }

    @Synchronized
    fun updateEntry(entry: VaultEntry) =
        mutate { list -> list.map { if (it.id == entry.id) entry else it } }

    @Synchronized
    fun deleteEntry(id: String) = mutate { list -> list.filterNot { it.id == id } }

    private fun mutate(change: (List<VaultEntry>) -> List<VaultEntry>) {
        val current = _session.value as? VaultSession.Unlocked ?: return
        val key = sessionKey ?: return
        val next = change(current.entries)
        prefs.edit()
            .putString(KEY_BLOB, key64(VaultCrypto.encrypt(key, serialize(next))))
            .apply()
        _session.value = VaultSession.Unlocked(next)
    }

    // endregion

    // region Biometric key wrap

    /** The IV prefix of the stored wrapped key, needed to rebuild the cipher. */
    fun wrappedKeyIv(): ByteArray? =
        wrappedKeyBlob()?.let { if (it.size > WRAP_IV_BYTES) it.copyOfRange(0, WRAP_IV_BYTES) else null }

    fun wrappedKeyBlob(): ByteArray? = stored(KEY_WRAPPED_KEY)

    /**
     * Encrypts the in-memory session key with an already-authenticated
     * biometric cipher and stores the result. The key never leaves this
     * class; the cipher only sees it through [Cipher.doFinal].
     */
    @Synchronized
    fun wrapSessionKey(cipher: Cipher): Boolean {
        val key = sessionKey ?: return false
        return try {
            val wrapped = cipher.iv + cipher.doFinal(key)
            prefs.edit()
                .putString(KEY_WRAPPED_KEY, key64(wrapped))
                .putBoolean(KEY_BIOMETRIC_ENABLED, true)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Reverses [wrapSessionKey] with an already-authenticated cipher. */
    @Synchronized
    fun unlockWithCipher(cipher: Cipher): Boolean {
        val blob = wrappedKeyBlob() ?: return false
        if (blob.size <= WRAP_IV_BYTES) return false
        val key = try {
            cipher.doFinal(blob, WRAP_IV_BYTES, blob.size - WRAP_IV_BYTES)
        } catch (e: Exception) {
            return false
        }
        return openWithKey(key)
    }

    @Synchronized
    fun disableBiometrics() {
        prefs.edit()
            .remove(KEY_WRAPPED_KEY)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .apply()
    }

    // endregion

    // region Serialisation

    private fun serialize(entries: List<VaultEntry>): ByteArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("username", entry.username)
                    .put("password", entry.password)
                    .put("url", entry.url)
                    .put("notes", entry.notes)
                    .put("updatedAt", entry.updatedAtMillis)
            )
        }
        return array.toString().toByteArray(Charsets.UTF_8)
    }

    private fun deserialize(bytes: ByteArray): List<VaultEntry> {
        val array = JSONArray(String(bytes, Charsets.UTF_8))
        return List(array.length()) { index ->
            val obj = array.getJSONObject(index)
            VaultEntry(
                id = obj.getString("id"),
                title = obj.optString("title"),
                username = obj.optString("username"),
                password = obj.optString("password"),
                url = obj.optString("url"),
                notes = obj.optString("notes"),
                updatedAtMillis = obj.optLong("updatedAt"),
            )
        }
    }

    private fun stored(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private fun key64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    // endregion

    private companion object {
        const val KEY_SALT = "salt"
        const val KEY_VERIFIER = "verifier"
        const val KEY_BLOB = "blob"
        const val KEY_WRAPPED_KEY = "wrapped_key"
        const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        const val WRAP_IV_BYTES = 12
    }
}
