package org.abodah.heymoney.vault

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cryptography of the local password vault. Everything here is plain
 * JVM (`javax.crypto`), no Android, so it is unit-testable off-device.
 *
 * Design:
 * - the master password is never stored; a key is derived from it with
 *   PBKDF2-HmacSHA256 ([PBKDF2_ITERATIONS] rounds, random salt);
 * - vault content is encrypted with AES-GCM (random IV per blob, the IV is
 *   prepended to the ciphertext);
 * - a *verifier* blob — the encryption of a known constant — lets the app
 *   check a typed master password (it decrypts iff the derived key is the
 *   right one) without storing the key or the password anywhere;
 * - a wrong key makes GCM decryption throw, which is how tampering and
 *   wrong passwords are detected.
 */
object VaultCrypto {

    const val PBKDF2_ITERATIONS = 250_000
    const val SALT_BYTES = 16

    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val VERIFIER_PLAINTEXT =
        "HeyMoney vault verifier v1".toByteArray(Charsets.UTF_8)

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** Derives the 256-bit vault key from the master password and salt. */
    fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Encrypts with AES-GCM; the result is `IV || ciphertext || tag`. */
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Reverses [encrypt]. Throws (AEADBadTagException) when the key is wrong
     * or the blob was tampered with — callers rely on that.
     */
    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "Vault blob too short" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }

    /** The blob stored next to the vault so a password can be checked. */
    fun makeVerifier(key: ByteArray): ByteArray = encrypt(key, VERIFIER_PLAINTEXT)

    /** True when [key] is the key that produced [verifier]. */
    fun checkVerifier(key: ByteArray, verifier: ByteArray): Boolean =
        try {
            decrypt(key, verifier).contentEquals(VERIFIER_PLAINTEXT)
        } catch (e: Exception) {
            false
        }
}
