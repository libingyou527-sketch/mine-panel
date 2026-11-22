package app.minepanel.core.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密备份包解密或校验失败：口令错误、文件被篡改、格式不兼容或内容损坏。
 * 不区分「口令错」与「被篡改」，避免侧信道泄露包内容是否有效。
 */
class BackupCryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 备份包对称加解密容器（与库内 SQLCipher / Keystore 包装相互独立）。
 *
 * 设计：
 * - 用户恢复口令经 PBKDF2-HMAC-SHA256 派生 AES-256 密钥；每份备份使用独立随机盐。
 * - 载荷以 AES/GCM/NoPadding 加密，GCM 认证标签随密文一同存储；口令错误或密文被篡改时解密失败。
 * - 容器为自描述二进制：魔数 / 版本 / KDF 标识 / 迭代次数 / 盐 / IV / 密文（含标签）。
 *
 * 本类只使用 JDK 密码学 API，不引用 Android 类型，便于在纯 JVM 单测中覆盖往返、错误口令与篡改路径。
 * 口令遗忘即不可恢复，无云端找回；本类不保存口令、不派生可导出的密钥。
 */
object BackupCipher {

    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val KEY_FACTORY = "PBKDF2WithHmacSHA256"
    private const val ALGORITHM = "AES"

    private val MAGIC = byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val ITERATIONS = 150_000

    /** 加密明文载荷；口令不可为空。返回自描述的备份容器字节。 */
    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        if (password.isEmpty()) throw BackupCryptoException("备份口令不能为空")
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val headerSize = MAGIC.size + 1 + 1 + 4 + salt.size + iv.size
        val buffer = ByteBuffer.allocate(headerSize + ciphertext.size)
        buffer.put(MAGIC)
        buffer.put(FORMAT_VERSION)
        buffer.put(KDF_PBKDF2_SHA256)
        buffer.putInt(ITERATIONS)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    /** 解密备份容器；口令错误、篡改、格式不符均抛 [BackupCryptoException]。 */
    fun decrypt(blob: ByteArray, password: String): ByteArray {
        if (password.isEmpty()) throw BackupCryptoException("恢复口令不能为空")
        val buffer = ByteBuffer.wrap(blob)
        try {
            val magic = ByteArray(MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAGIC)) throw BackupCryptoException("备份文件格式不兼容")

            val version = buffer.get()
            if (version != FORMAT_VERSION) throw BackupCryptoException("备份格式版本不受支持：$version")

            val kdf = buffer.get()
            if (kdf != KDF_PBKDF2_SHA256) throw BackupCryptoException("备份密钥派生方式不受支持")

            val iterations = buffer.int
            require(iterations in 1..1_000_000) { "迭代次数非法" }

            val salt = ByteArray(SALT_BYTES)
            buffer.get(salt)
            val iv = ByteArray(IV_BYTES)
            buffer.get(iv)

            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)

            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: BackupCryptoException) {
            throw e
        } catch (e: Exception) {
            // AEADBadTagException / 非法参数 / 缓冲区越界等统一归为不可解。
            throw BackupCryptoException("口令错误或备份文件已损坏", e)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_FACTORY)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BYTES * 8)
        val derived = factory.generateSecret(spec)
        return SecretKeySpec(derived.encoded, ALGORITHM)
    }
}
