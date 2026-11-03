package org.abodah.heymoney.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {

    private val password = "correct horse battery staple".toCharArray()
    private val otherPassword = "Tr0ub4dor&3".toCharArray()

    @Test
    fun `encrypt decrypt round trip with the derived key`() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        val plaintext = "[{\"title\":\"mail\",\"password\":\"s3cret\"}]".toByteArray()

        val blob = VaultCrypto.encrypt(key, plaintext)

        assertFalse(blob.contentEquals(plaintext))
        assertArrayEquals(plaintext, VaultCrypto.decrypt(key, blob))
    }

    @Test
    fun `verifier accepts the right password and rejects a wrong one`() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        val verifier = VaultCrypto.makeVerifier(key)

        assertTrue(VaultCrypto.checkVerifier(key, verifier))

        val wrongKey = VaultCrypto.deriveKey(otherPassword, salt)
        assertFalse(VaultCrypto.checkVerifier(wrongKey, verifier))
    }

    @Test
    fun `decrypting with a wrong key throws instead of returning garbage`() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        val blob = VaultCrypto.encrypt(key, "secret".toByteArray())
        val wrongKey = VaultCrypto.deriveKey(otherPassword, salt)

        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(wrongKey, blob)
        }
    }

    @Test
    fun `a tampered blob is rejected`() {
        val salt = VaultCrypto.newSalt()
        val key = VaultCrypto.deriveKey(password, salt)
        val blob = VaultCrypto.encrypt(key, "secret".toByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()

        assertThrows(Exception::class.java) {
            VaultCrypto.decrypt(key, blob)
        }
    }

    @Test
    fun `different salts derive different keys`() {
        val keyA = VaultCrypto.deriveKey(password, VaultCrypto.newSalt())
        val keyB = VaultCrypto.deriveKey(password, VaultCrypto.newSalt())
        assertFalse(keyA.contentEquals(keyB))
    }
}

class PasswordGeneratorTest {

    @Test
    fun `generated passwords have the exact requested length`() {
        repeat(50) {
            assertEquals(
                20,
                PasswordGenerator.generate(PasswordOptions(length = 20)).length
            )
            assertEquals(
                8,
                PasswordGenerator.generate(PasswordOptions(length = 8)).length
            )
        }
    }

    @Test
    fun `only the selected character sets are used`() {
        val digitsOnly = PasswordGenerator.generate(
            PasswordOptions(length = 24, lower = false, upper = false, digits = true)
        )
        assertTrue(digitsOnly.all { it.isDigit() })

        val noSymbols = PasswordGenerator.generate(
            PasswordOptions(length = 24, symbols = false)
        )
        assertTrue(noSymbols.all { it.isLetterOrDigit() })
    }

    @Test
    fun `every selected set appears at least once`() {
        repeat(100) {
            val generated = PasswordGenerator.generate(
                PasswordOptions(
                    length = 12,
                    lower = true,
                    upper = true,
                    digits = true,
                    symbols = true,
                )
            )
            assertTrue(generated.any { it.isLowerCase() })
            assertTrue(generated.any { it.isUpperCase() })
            assertTrue(generated.any { it.isDigit() })
            assertTrue(generated.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `a too short length is raised so every set fits`() {
        val generated = PasswordGenerator.generate(
            PasswordOptions(
                length = 2,
                lower = true,
                upper = true,
                digits = true,
                symbols = true,
            )
        )
        assertTrue(generated.length >= 4)
    }
}
