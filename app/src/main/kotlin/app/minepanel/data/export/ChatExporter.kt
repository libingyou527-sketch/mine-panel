package app.minepanel.data.export

/**
 * 聊天独立导出包 JSON 序列化器。
 *
 * 与综合导出包同一手写风格：字段固定、层级浅，不引入 JSON 库；字符串转义复用 [JsonExporter.escape]。
 * 导出包可由 [ChatImportParser] 回读恢复；候选要点不在包内。
 */
object ChatExporter {

    const val SCHEMA_VERSION = 1

    fun toJson(bundle: ChatExportBundle): String = buildString {
        append('{')
        appendKeyValue("schemaVersion", bundle.schemaVersion)
        append(',')
        appendKeyValue("exportedAt", bundle.exportedAt)
        append(',')

        appendKey("sessionTitle")
        if (bundle.sessionTitle == null) append("null") else appendString(bundle.sessionTitle)
        append(',')

        appendKey("messages")
        appendArray(bundle.messages) { message ->
            append('{')
            appendStringKeyValue("role", message.role)
            append(',')
            appendStringKeyValue("content", message.content)
            append(',')
            appendKeyValue("createdAt", message.createdAt)
            append('}')
        }
        append(',')

        appendKey("memories")
        appendArray(bundle.memories) { memory ->
            append('{')
            appendStringKeyValue("content", memory.content)
            append(',')
            appendKeyValue("createdAt", memory.createdAt)
            append('}')
        }

        append('}')
    }

    private fun StringBuilder.appendKey(key: String) {
        append('"').append(JsonExporter.escape(key)).append("\":")
    }

    private fun StringBuilder.appendStringKeyValue(key: String, value: String) {
        appendKey(key)
        appendString(value)
    }

    private fun StringBuilder.appendKeyValue(key: String, value: Number) {
        appendKey(key)
        append(value)
    }

    private fun <T> StringBuilder.appendArray(
        items: List<T>,
        appendItem: StringBuilder.(T) -> Unit
    ) {
        append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            appendItem(item)
        }
        append(']')
    }

    private fun StringBuilder.appendString(value: String) {
        append('"').append(JsonExporter.escape(value)).append('"')
    }
}
