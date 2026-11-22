package app.minepanel.model

import java.security.MessageDigest

/** 对精简输入片段计算 SHA-256 摘要；运行日志只留存哈希，不存原始事件流。 */
object ModelSnapshotHasher {

    fun hash(input: ModelInput): String {
        val canonical = PromptBuilder.compactJson(input)
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
