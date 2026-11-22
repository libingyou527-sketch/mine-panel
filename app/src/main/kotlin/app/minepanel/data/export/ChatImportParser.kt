package app.minepanel.data.export

import app.minepanel.core.model.ChatRole

/** 解析成功后的待导入聊天条目；尚未与本地库查重。 */
data class ParsedChatImport(
    val messages: List<ExportedChatMessage>,
    val memories: List<ExportedChatMemory>,
    val sessionTitle: String?
)

/**
 * 聊天独立导出 JSON 的解析与 schema 校验。
 *
 * 只把文本解析为强类型待导入条目，不触碰数据库；版本不兼容、JSON 非法、字段缺失或类型不符抛 [ImportException]。
 * 与综合导出包解析相互独立；候选要点不在导出包内，因此也不导入。
 */
object ChatImportParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parse(text: String): ParsedChatImport {
        val root = try {
            MiniJson.parse(text)
        } catch (e: Exception) {
            throw ImportException("无法解析 JSON：${e.message ?: "格式错误"}")
        }.asObject() ?: throw ImportException("导入文件根节点必须是对象")

        val version = root.long("schemaVersion")
            ?: throw ImportException("缺少 schemaVersion 字段")
        if (version != SUPPORTED_SCHEMA_VERSION.toLong()) {
            throw ImportException("不支持的聊天导出版本 $version（当前支持 $SUPPORTED_SCHEMA_VERSION）")
        }

        val messages = root.optionalArray("messages")?.map { it.toMessage() } ?: emptyList()
        val memories = root.optionalArray("memories")?.map { it.toMemory() } ?: emptyList()
        val sessionTitle = root.map["sessionTitle"].asNullableString()

        return ParsedChatImport(
            messages = messages,
            memories = memories,
            sessionTitle = sessionTitle
        )
    }

    private fun JsonValue.Obj.long(field: String): Long? =
        (map[field] as? JsonValue.Num)?.raw?.toLongOrNull()

    private fun JsonValue.Obj.optionalArray(field: String): List<JsonValue>? =
        (map[field] as? JsonValue.Arr)?.items

    private fun JsonValue.toMessage(): ExportedChatMessage {
        val o = asObject() ?: throw ImportException("消息条目必须是对象")
        val rawRole = o.map["role"].asNullableString()
        val role = runCatching { ChatRole.valueOf(rawRole ?: "") }.getOrDefault(ChatRole.USER).name
        return ExportedChatMessage(
            role = role,
            content = o.map["content"].asStringOrThrow("content"),
            createdAt = o.long("createdAt") ?: 0L
        )
    }

    private fun JsonValue.toMemory(): ExportedChatMemory {
        val o = asObject() ?: throw ImportException("要点条目必须是对象")
        return ExportedChatMemory(
            content = o.map["content"].asStringOrThrow("content"),
            createdAt = o.long("createdAt") ?: 0L
        )
    }

    private fun JsonValue?.asStringOrThrow(field: String): String =
        (this as? JsonValue.Str)?.value ?: throw ImportException("字段缺失或类型错误：$field")

    private fun JsonValue?.asNullableString(): String? = when (this) {
        is JsonValue.Str -> value
        JsonValue.Null -> null
        else -> null
    }
}
