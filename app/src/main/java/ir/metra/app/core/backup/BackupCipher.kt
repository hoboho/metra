package ir.metra.app.core.backup

import ir.metra.app.core.common.MetraError
import ir.metra.app.core.common.MetraResult
import ir.metra.app.core.common.failure
import ir.metra.app.core.common.success
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional passphrase protection for backup files.
 *
 * Scheme: PBKDF2-HMAC-SHA256 (120,000 iterations, 16-byte random salt) derives a
 * 256-bit AES key, and the payload is sealed with AES-256-GCM. GCM is used
 * because it authenticates as well as encrypts, so a tampered or truncated file
 * fails loudly instead of importing garbage.
 *
 * File layout: `MAGICTAG | 2-byte salt length | salt | 12-byte IV | ciphertext`.
 */
@Singleton
class BackupCipher @Inject constructor() {

    /** Encrypts [plain] with a key derived from [passphrase]. */
    fun encrypt(plain: ByteArray, passphrase: String): MetraResult<ByteArray> = runCatching {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plain)
        success(MAGIC + salt.size.toShortBytes() + salt + iv + ciphertext)
    }.fold({ it }, { failure(MetraError.Backup(it.message ?: "encryption failed")) })

    /** Decrypts a file produced by [encrypt]. */
    fun decrypt(payload: ByteArray, passphrase: String): MetraResult<ByteArray> = runCatching {
        if (!payload.startsWith(MAGIC)) {
            return failure(MetraError.InvalidBackup("ساختار فایل پشتیبان ناشناخته است"))
        }
        var offset = MAGIC.size
        if (payload.size < offset + 2) {
            return failure(MetraError.InvalidBackup("فایل پشتیبان ناقص است"))
        }
        val saltLength = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        offset += 2
        if (payload.size < offset + saltLength + IV_BYTES) {
            return failure(MetraError.InvalidBackup("فایل پشتیبان ناقص است"))
        }
        val salt = payload.copyOfRange(offset, offset + saltLength)
        offset += saltLength
        val iv = payload.copyOfRange(offset, offset + IV_BYTES)
        offset += IV_BYTES
        val ciphertext = payload.copyOfRange(offset, payload.size)

        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        try {
            success(cipher.doFinal(ciphertext))
        } catch (authFailure: javax.crypto.AEADBadTagException) {
            // Wrong passphrase and tampering are indistinguishable by design.
            failure(MetraError.WrongPassphrase)
        }
    }.fold({ it }, { failure(MetraError.Restore(it.message ?: "decryption failed")) })

    /** True when [payload] carries the encryption envelope. */
    fun isEncrypted(payload: ByteArray): Boolean = payload.startsWith(MAGIC)

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun Int.toShortBytes(): ByteArray = byteArrayOf((this shr 8).toByte(), (this and 0xFF).toByte())

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) return false
        }
        return true
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private val MAGIC = "METRAENC1".toByteArray(Charsets.US_ASCII)
    }
}

/** Small helper so callers do not have to care about temporary files. */
fun File.writeBytesOrThrow(bytes: ByteArray) {
    parentFile?.mkdirs()
    writeBytes(bytes)
}
