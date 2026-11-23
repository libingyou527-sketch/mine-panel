package app.minepanel.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * 数据库口令包装：
 * - 首次生成 32 字节随机 SQLCipher 口令；
 * - 用 Android Keystore 内不可导出的 AES-256（GCM）密钥加密后，密文连同 IV 存私有目录；
 * - 后续用同一 Keystore 密钥解密还原口令。口令明文不落地、不硬编码。
 *
 * 说明：早期版本用 RSA 公钥包装/私钥解密，在部分 HyperOS/Keystore2 机型上密钥条目仍在、
 * 但私钥解密算子在进程重启后返回 -1000 UNKNOWN_ERROR，故改为更基础的 AES-GCM 对称通路。
 */
class KeyStoreManager(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val wrapFile: File = File(context.filesDir, WRAP_FILE)

    fun getOrCreatePassphrase(): ByteArray {
        val stored = readWrapped()
        if (stored != null) {
            return try {
                unwrap(stored)
            } catch (e: Exception) {
                // 包装不可还原：不静默生成新口令（否则打不开旧加密库），
                // 交由上层提示用户确认后再擦除本地数据。
                Log.e(
                    "KeyStoreManager",
                    "unwrap failed; aliasPresent=${keyStore.containsAlias(ALIAS)}; wrappedBytes=${stored.size}",
                    e
                )
                throw SecureStorageUnavailableException(
                    "数据库口令包装已失效，无法从系统密钥库还原", e
                )
            }
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        writeWrapped(wrap(passphrase))
        return passphrase
    }

    private fun ensureKey() {
        if (keyStore.containsAlias(ALIAS)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        generator.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        ensureKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getKey(ALIAS, null))
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + ct.size).also {
            it[0] = FORMAT_VERSION
            System.arraycopy(iv, 0, it, 1, iv.size)
            System.arraycopy(ct, 0, it, 1 + iv.size, ct.size)
        }
    }

    private fun unwrap(wrapped: ByteArray): ByteArray {
        ensureKey()
        if (wrapped.isEmpty() || wrapped[0] != FORMAT_VERSION) {
            throw IllegalStateException("口令包装格式不兼容")
        }
        val iv = wrapped.copyOfRange(1, 1 + IV_LENGTH)
        val ct = wrapped.copyOfRange(1 + IV_LENGTH, wrapped.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyStore.getKey(ALIAS, null),
            GCMParameterSpec(TAG_BITS, iv)
        )
        return cipher.doFinal(ct)
    }

    /** 加密一段 UTF-8 字符串，返回 Base64 密文；明文不落地。用于外部模型 API Key 等本地凭据。 */
    fun wrapString(plain: String): String {
        val wrapped = wrap(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(wrapped, Base64.NO_WRAP)
    }

    /** 解开 [wrapString] 产出的 Base64 密文；包装失效时抛异常，由上层按不可用处理。 */
    fun unwrapString(wrappedBase64: String): String {
        val bytes = Base64.decode(wrappedBase64.trim(), Base64.NO_WRAP)
        return String(unwrap(bytes), Charsets.UTF_8)
    }

    /** 撤销密钥并删除包装文件；下次取口令时生成全新密钥与随机口令。 */
    fun reset() {
        if (keyStore.containsAlias(ALIAS)) {
            keyStore.deleteEntry(ALIAS)
        }
        if (wrapFile.exists()) {
            wrapFile.delete()
        }
    }

    private fun readWrapped(): ByteArray? {
        if (!wrapFile.exists()) return null
        val text = wrapFile.readText().trim()
        if (text.isEmpty()) return null
        return Base64.decode(text, Base64.NO_WRAP)
    }

    private fun writeWrapped(data: ByteArray) {
        wrapFile.writeText(Base64.encodeToString(data, Base64.NO_WRAP))
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "minepanel_wrap_key_aes"
        private const val WRAP_FILE = "db_wrap.bin"
        private const val PASSPHRASE_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION = 1.toByte()
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
    }
}
