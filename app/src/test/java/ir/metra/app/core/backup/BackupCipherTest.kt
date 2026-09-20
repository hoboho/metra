package ir.metra.app.core.backup

import com.google.common.truth.Truth.assertThat
import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.metraError
import org.junit.Test

/**
 * Backup encryption.
 *
 * The security properties that matter for this feature: a correct passphrase
 * round-trips byte-for-byte, a wrong passphrase fails with a distinguishable
 * error (not corrupted data), and any tampering with the ciphertext is
 * detected rather than decrypted into garbage.
 */
class BackupCipherTest {

    private val cipher = BackupCipher()

    private val payload = """{"projects":[],"workRecords":[],"expenses":[]}""".toByteArray(Charsets.UTF_8)

    @Test
    fun `encrypt then decrypt returns the original bytes`() {
        val encrypted = cipher.encrypt(payload, "passphrase-۱۲۳").getOrThrow()
        val decrypted = cipher.decrypt(encrypted, "passphrase-۱۲۳").getOrThrow()
        assertThat(decrypted).isEqualTo(payload)
    }

    @Test
    fun `encrypted output is marked with the magic header`() {
        val encrypted = cipher.encrypt(payload, "secret").getOrThrow()
        assertThat(cipher.isEncrypted(encrypted)).isTrue()
        assertThat(cipher.isEncrypted(payload)).isFalse()
    }

    @Test
    fun `the ciphertext does not contain the plaintext`() {
        val encrypted = cipher.encrypt(payload, "secret").getOrThrow()
        assertThat(String(encrypted, Charsets.ISO_8859_1)).doesNotContain("workRecords")
    }

    @Test
    fun `the same plaintext encrypts differently each time`() {
        val first = cipher.encrypt(payload, "secret").getOrThrow()
        val second = cipher.encrypt(payload, "secret").getOrThrow()
        // A fresh IV per encryption, so identical inputs do not produce
        // identical ciphertext.
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `a wrong passphrase fails rather than returning garbage`() {
        val encrypted = cipher.encrypt(payload, "correct-passphrase").getOrThrow()
        val result = cipher.decrypt(encrypted, "wrong-passphrase")
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.metraError).isInstanceOf(MetraError.WrongPassphrase::class.java)
    }

    @Test
    fun `tampering with the ciphertext is detected`() {
        val encrypted = cipher.encrypt(payload, "secret").getOrThrow()
        val tampered = encrypted.copyOf().also {
            // Flip a bit in the middle of the ciphertext.
            val index = it.size / 2
            it[index] = (it[index].toInt() xor 0x01).toByte()
        }
        assertThat(cipher.decrypt(tampered, "secret").isFailure).isTrue()
    }

    @Test
    fun `a truncated payload fails cleanly`() {
        val encrypted = cipher.encrypt(payload, "secret").getOrThrow()
        val truncated = encrypted.copyOfRange(0, encrypted.size / 2)
        assertThat(cipher.decrypt(truncated, "secret").isFailure).isTrue()
    }

    @Test
    fun `an empty payload round-trips`() {
        val encrypted = cipher.encrypt(ByteArray(0), "secret").getOrThrow()
        assertThat(cipher.decrypt(encrypted, "secret").getOrThrow()).isEmpty()
    }

    @Test
    fun `a large payload round-trips`() {
        val large = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val encrypted = cipher.encrypt(large, "secret").getOrThrow()
        assertThat(cipher.decrypt(encrypted, "secret").getOrThrow()).isEqualTo(large)
    }

    @Test
    fun `an empty passphrase still round-trips`() {
        // Passphrase strength is a UI concern (the dialog enforces a minimum
        // length); the cipher itself must not silently alter the payload.
        val encrypted = cipher.encrypt(payload, "").getOrThrow()
        assertThat(cipher.decrypt(encrypted, "").getOrThrow()).isEqualTo(payload)
    }

    @Test
    fun `a passphrase shorter than the magic header fails as invalid backup`() {
        val result = cipher.decrypt("x".toByteArray(), "secret")
        assertThat(result.isFailure).isTrue()
    }
}
