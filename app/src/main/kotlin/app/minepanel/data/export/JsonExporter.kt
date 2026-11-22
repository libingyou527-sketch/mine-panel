package app.minepanel.data.export

/**
 * 导出包 JSON 序列化器。
 *
 * 不引入 JSON 库：字段结构固定、层级很浅，手写拼接即可保证输出可控，
 * 也让序列化逻辑能在纯 JVM 单测中验证字符串转义与字段完整性。
 * 解析（导入侧）在后续任务单独实现，与本序列化器对称。
 */
object JsonExporter {

    const val SCHEMA_VERSION = 1

    fun toJson(bundle: ExportBundle): String = buildString {
        append('{')
        appendKeyValue("schemaVersion", bundle.schemaVersion)
        append(',')
        appendKeyValue("exportedAt", bundle.exportedAt)
        append(',')
        appendStringKeyValue("appVersion", bundle.appVersion)
        append(',')

        appendKey("confirmedMemories")
        appendArray(bundle.confirmedMemories) { memory ->
            append('{')
            appendKeyValue("id", memory.id)
            append(',')
            appendStringKeyValue("content", memory.content)
            append(',')
            appendKeyValue("periodStart", memory.periodStart)
            append(',')
            appendKeyValue("periodEnd", memory.periodEnd)
            append(',')
            appendKeyValue("createdAt", memory.createdAt)
            append('}')
        }
        append(',')

        appendKey("statusTags")
        appendArray(bundle.statusTags) { tag ->
            append('{')
            appendKeyValue("id", tag.id)
            append(',')
            appendStringKeyValue("label", tag.label)
            append(',')
            appendKeyValue("createdAt", tag.createdAt)
            append(',')
            appendNullableLong("reviewedAt", tag.reviewedAt)
            append('}')
        }
        append(',')

        appendKey("narratives")
        appendArray(bundle.narratives) { narrative ->
            append('{')
            appendKeyValue("id", narrative.id)
            append(',')
            appendStringKeyValue("content", narrative.content)
            append(',')
            appendKeyValue("createdAt", narrative.createdAt)
            append(',')
            appendNullableLong("reviewedAt", narrative.reviewedAt)
            append('}')
        }
        append(',')

        appendKey("attributeScores")
        appendArray(bundle.attributeScores) { score ->
            append('{')
            appendStringKeyValue("attributeKey", score.attributeKey)
            append(',')
            appendKeyValue("periodStart", score.periodStart)
            append(',')
            appendKeyValue("periodEnd", score.periodEnd)
            append(',')
            appendKeyValue("score", score.score)
            append(',')
            appendKeyValue("confidence", score.confidence)
            append(',')
            appendStringKeyValue("evidenceJson", score.evidenceJson)
            append(',')
            appendKeyValue("sampleDays", score.sampleDays)
            append(',')
            appendKeyValue("updatedAt", score.updatedAt)
            append('}')
        }
        append(',')

        appendKey("badges")
        appendArray(bundle.badges) { badge ->
            append('{')
            appendStringKeyValue("badgeKey", badge.badgeKey)
            append(',')
            appendKeyValue("unlockedAt", badge.unlockedAt)
            append(',')
            appendStringKeyValue("conditionSnapshotJson", badge.conditionSnapshotJson)
            append('}')
        }
        append(',')

        appendKey("manualFragments")
        appendArray(bundle.manualFragments) { fragment ->
            append('{')
            appendStringKeyValue("content", fragment.content)
            append(',')
            appendNullableString("mood", fragment.mood)
            append(',')
            appendNullableString("category", fragment.category)
            append(',')
            appendNullableLong("anchorEpochMs", fragment.anchorEpochMs)
            append(',')
            appendKeyValue("createdAt", fragment.createdAt)
            append(',')
            appendKeyValue("updatedAt", fragment.updatedAt)
            append('}')
        }
        append(',')

        appendKey("milestones")
        appendArray(bundle.milestones) { milestone ->
            append('{')
            appendStringKeyValue("title", milestone.title)
            append(',')
            appendNullableString("label", milestone.label)
            append(',')
            appendNullableString("note", milestone.note)
            append(',')
            appendNullableLong("anchorEpochMs", milestone.anchorEpochMs)
            append(',')
            appendKeyValue("createdAt", milestone.createdAt)
            append(',')
            appendKeyValue("updatedAt", milestone.updatedAt)
            append('}')
        }

        bundle.rawEvents?.let { rawEvents ->
            append(',')
            appendKey("rawEvents")
            appendArray(rawEvents) { event ->
                append('{')
                appendKeyValue("timestamp", event.timestamp)
                append(',')
                appendStringKeyValue("eventType", event.eventType)
                append(',')
                appendStringKeyValue("category", event.category)
                append(',')
                appendKeyValue("durationMs", event.durationMs)
                append(',')
                appendNullableString("sessionId", event.sessionId)
                append('}')
            }
        }

        append('}')
    }

    private fun StringBuilder.appendKey(key: String) {
        append('"').append(escape(key)).append("\":")
    }

    private fun StringBuilder.appendStringKeyValue(key: String, value: String) {
        appendKey(key)
        appendString(value)
    }

    private fun StringBuilder.appendKeyValue(key: String, value: Number) {
        appendKey(key)
        append(value)
    }

    private fun StringBuilder.appendNullableLong(key: String, value: Long?) {
        appendKey(key)
        if (value == null) append("null") else append(value)
    }

    private fun StringBuilder.appendNullableString(key: String, value: String?) {
        appendKey(key)
        if (value == null) append("null") else appendString(value)
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
        append('"').append(escape(value)).append('"')
    }

    /** JSON 字符串转义：引号/反斜杠/控制字符转义，其余字符原样保留（含中文）。 */
    internal fun escape(raw: String): String = buildString {
        raw.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (c.code < 0x20) {
                        append("\\u%04X".format(c.code))
                    } else {
                        append(c)
                    }
                }
            }
        }
    }
}
