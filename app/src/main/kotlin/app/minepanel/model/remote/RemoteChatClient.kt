package app.minepanel.model.remote

import app.minepanel.data.export.MiniJson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 一条发给远端的结构化消息；role 为 system/user/assistant。 */
data class RemoteMessage(val role: String, val content: String)

/**
 * OpenAI 兼容 /chat/completions 客户端。不引入网络库，基于 HttpURLConnection；
 * 仅在用户显式启用外部模型且配置完整时调用，任何失败都由上层降级到内置模型。
 */
class RemoteChatClient(
    private val config: RemoteChatConfig,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMs: Int = 30_000
) {

    /** 发送一轮结构化对话，返回远端回复正文；非 2xx、缺字段或网络异常均抛异常。 */
    suspend fun chat(messages: List<RemoteMessage>): String = withContext(ioDispatcher) {
        if (!config.isComplete) throw IllegalStateException("远程模型配置不完整")
        val body = buildRequestBody(config.model, messages)
        val connection = (URL(config.endpoint()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val payload = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("远端返回 HTTP $code")
            }
            parseResponseBody(payload)
        } finally {
            connection.disconnect()
        }
    }

    companion object {

        /** 构造请求体；抽成纯函数便于 JVM 单测覆盖转义与结构。 */
        internal fun buildRequestBody(model: String, messages: List<RemoteMessage>): String {
            val sb = StringBuilder()
            sb.append("{\"model\":").append(jsonString(model))
            sb.append(",\"messages\":[")
            messages.forEachIndexed { index, message ->
                if (index > 0) sb.append(',')
                sb.append("{\"role\":").append(jsonString(message.role))
                sb.append(",\"content\":").append(jsonString(message.content)).append('}')
            }
            sb.append("],\"temperature\":0.7,\"stream\":false}")
            return sb.toString()
        }

        /** 从 OpenAI 兼容响应中取 choices[0].message.content；缺失或非法时抛异常。 */
        internal fun parseResponseBody(text: String): String {
            val root = MiniJson.parse(text).asObject()
                ?: throw IllegalStateException("响应不是 JSON 对象")
            val content = root.map["choices"]?.asArray()?.items?.firstOrNull()
                ?.asObject()?.map?.get("message")?.asObject()?.map?.get("content")?.asString()
                ?: throw IllegalStateException("响应缺少 choices[0].message.content")
            return content.trim()
        }

        internal fun jsonString(value: String): String {
            val sb = StringBuilder("\"")
            value.forEach { ch ->
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            return sb.append('"').toString()
        }
    }
}
