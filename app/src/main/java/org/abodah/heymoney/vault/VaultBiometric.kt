package org.abodah.heymoney.vault

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Biometric reopening for the vault, built on the androidx.biometric
 * library the app already uses for its app lock.
 *
 * A random AES key sits in the Android Keystore, generated with
 * "user authentication required": it can only be used inside a
 * [BiometricPrompt] CryptoObject. The vault uses it to *wrap* (encrypt) a
 * copy of the master-password-derived key while the vault is open, and to
 * unwrap it on a later biometric success. The wrapped copy is useless
 * without a live biometric authentication, and the master password keeps
 * working whatever happens to it (new fingerprint enrolled, key
 * invalidated, biometrics removed…).
 */
object VaultBiometric {

    private const val KEY_ALIAS = "heymoney_vault_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS

    /** A cipher ready to wrap the vault key, to hand to [authenticate]. */
    fun cipherForWrap(): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keystoreKey())
        }

    /** A cipher ready to unwrap, rebuilt with the IV stored at wrap time. */
    fun cipherForUnwrap(iv: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(builder.build()) }
            .generateKey()
    }

    /**
     * Shows the biometric prompt for [cipher]; [onSuccess] receives the
     * *authenticated* cipher, the only one allowed to use the keystore key.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelText: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: () -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated != null) onSuccess(authenticated) else onError()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) = onError()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(cancelText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
