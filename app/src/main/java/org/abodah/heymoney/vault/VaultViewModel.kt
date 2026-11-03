package org.abodah.heymoney.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.crypto.Cipher
import javax.inject.Inject

/** Why creating the vault was refused. */
enum class VaultCreateError { TOO_SHORT, MISMATCH }

/**
 * State and actions of the password vault screen. All cryptography lives in
 * [VaultStore] / [VaultCrypto]; this class only validates input, runs the
 * slow key derivation off the main thread, and implements the auto-lock
 * policy: the vault locks when the screen is left, and when the app comes
 * back to the foreground after more than [AUTO_LOCK_MS] in the background.
 */
@HiltViewModel
class VaultViewModel @Inject constructor(
    private val store: VaultStore,
) : ViewModel() {

    val session: StateFlow<VaultSession> = store.session

    private val _unlockFailed = MutableStateFlow(false)
    val unlockFailed: StateFlow<Boolean> = _unlockFailed

    private val _createError = MutableStateFlow<VaultCreateError?>(null)
    val createError: StateFlow<VaultCreateError?> = _createError

    private val _biometricEnabled = MutableStateFlow(store.biometricEnabled)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled

    private var backgroundedAtMillis: Long? = null

    // region Create / unlock / lock

    fun create(password: String, confirmation: String) {
        _createError.value = when {
            password.length < MIN_MASTER_PASSWORD_LENGTH -> VaultCreateError.TOO_SHORT
            password != confirmation -> VaultCreateError.MISMATCH
            else -> null
        }
        if (_createError.value != null) return
        viewModelScope.launch(Dispatchers.Default) {
            store.createVault(password.toCharArray())
        }
    }

    fun unlock(password: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val ok = store.unlock(password.toCharArray())
            _unlockFailed.value = !ok
        }
    }

    fun lock() {
        _unlockFailed.value = false
        store.lock()
    }

    fun onBackground() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    fun onForeground() {
        val since = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        if (session.value is VaultSession.Unlocked &&
            System.currentTimeMillis() - since > AUTO_LOCK_MS
        ) {
            lock()
        }
    }

    // endregion

    // region Entries

    fun addEntry(
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
    ) {
        store.addEntry(
            VaultEntry(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                username = username.trim(),
                password = password,
                url = url.trim(),
                notes = notes.trim(),
                updatedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    fun updateEntry(
        entry: VaultEntry,
        title: String,
        username: String,
        password: String,
        url: String,
        notes: String,
    ) {
        store.updateEntry(
            entry.copy(
                title = title.trim(),
                username = username.trim(),
                password = password,
                url = url.trim(),
                notes = notes.trim(),
                updatedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    fun deleteEntry(entry: VaultEntry) = store.deleteEntry(entry.id)

    fun generatePassword(options: PasswordOptions): String =
        PasswordGenerator.generate(options)

    // endregion

    // region Biometrics

    fun wrappedKeyIv(): ByteArray? = store.wrappedKeyIv()

    /** Called with the authenticated cipher after a successful prompt. */
    fun enableBiometrics(cipher: Cipher) {
        if (store.wrapSessionKey(cipher)) {
            _biometricEnabled.value = true
        }
    }

    /** Called with the authenticated cipher after a successful prompt. */
    fun unlockWithBiometrics(cipher: Cipher) {
        val ok = store.unlockWithCipher(cipher)
        _unlockFailed.value = !ok
    }

    fun disableBiometrics() {
        store.disableBiometrics()
        _biometricEnabled.value = false
    }

    // endregion

    companion object {
        const val MIN_MASTER_PASSWORD_LENGTH = 8
        const val AUTO_LOCK_MS = 120_000L
    }
}
