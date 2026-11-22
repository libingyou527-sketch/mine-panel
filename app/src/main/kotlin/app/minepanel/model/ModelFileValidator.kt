package app.minepanel.model

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 本地 GGUF 模型文件校验：存在性、体积、SHA-256 基线比对。
 * 哈希不符或文件过小时拒绝加载；模型文件不进 APK、不下载、不上传。
 */
object ModelFileValidator {

    /** 基线模型：qwen2.5-0.5b-instruct-q4_k_m.gguf（491,400,032 字节）。 */
    const val BASELINE_SHA256 =
        "74A4DA8C9FDBCD15BD1F6D01D621410D31C6FC00986F5EB687824E7B93D7A9DB"

    private const val MIN_SIZE_BYTES = 100_000_000L

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verify(file: File?): VerifyResult {
        if (file == null || !file.exists() || !file.isFile) {
            return VerifyResult(valid = false, hash = null, reason = "模型文件不存在")
        }
        if (file.length() < MIN_SIZE_BYTES) {
            return VerifyResult(valid = false, hash = null, reason = "文件过小，不是有效的 GGUF 模型")
        }
        val hash = sha256(file)
        if (!hash.equals(BASELINE_SHA256, ignoreCase = true)) {
            return VerifyResult(valid = false, hash = hash, reason = "哈希与基线不符，拒绝加载")
        }
        return VerifyResult(valid = true, hash = hash, reason = "校验通过")
    }
}

data class VerifyResult(
    val valid: Boolean,
    val hash: String?,
    val reason: String
)
