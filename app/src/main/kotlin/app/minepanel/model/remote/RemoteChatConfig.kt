package app.minepanel.model.remote

/**
 * 外部（联网）聊天模型配置。[apiKey] 为明文，仅在内存与调用期间存在；
 * 落库前由上层经 Keystore 包装，不写入偏好明文。
 */
data class RemoteChatConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    /** 三项齐全才视为可调用；任一为空则不发起网络请求，直接降级内置模型。 */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** OpenAI 兼容聊天补全端点；去掉末尾斜杠后拼接固定路径。 */
    fun endpoint(): String = baseUrl.trimEnd('/') + "/chat/completions"

    companion object {
        val EMPTY = RemoteChatConfig("", "", "")
    }
}
