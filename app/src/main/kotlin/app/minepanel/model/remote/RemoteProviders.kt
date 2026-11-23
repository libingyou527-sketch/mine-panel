package app.minepanel.model.remote

/** 预置的 OpenAI 兼容厂商模板；[baseUrl] 为接口根地址，客户端会拼接 /chat/completions。 */
data class RemoteProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String
)

object RemoteProviders {

    val presets: List<RemoteProviderPreset> = listOf(
        RemoteProviderPreset("custom", "自定义（OpenAI 兼容）", "", ""),
        RemoteProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        RemoteProviderPreset("qwen", "通义千问（百炼）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        RemoteProviderPreset("kimi", "Kimi（月之暗面）", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        RemoteProviderPreset("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        RemoteProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", ""),
        RemoteProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini")
    )

    fun byId(id: String): RemoteProviderPreset =
        presets.firstOrNull { it.id == id } ?: presets.first()
}
