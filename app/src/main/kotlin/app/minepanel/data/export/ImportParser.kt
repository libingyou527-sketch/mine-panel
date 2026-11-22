package app.minepanel.data.export

/** 导入冲突策略：默认跳过重复、追加新条目，不覆盖已有正式记忆。 */
enum class ImportConflictStrategy {
    /** 重复条目跳过，仅追加库中不存在的条目（默认）。 */
    SKIP_DUPLICATES,

    /** 同主键/同指纹条目覆盖已有记录。 */
    OVERWRITE,

    /** 全部追加，不去重（可能产生重复条目）。 */
    APPEND_ALL
}

/** 导入解析或校验失败原因。 */
class ImportException(message: String) : Exception(message)

/** 解析成功后的待导入条目集合；尚未与本地库查重。 */
data class ParsedImport(
    val memories: List<ExportedMemory>,
    val tags: List<ExportedTag>,
    val narratives: List<ExportedNarrative> = emptyList(),
    val scores: List<ExportedScore>,
    val badges: List<ExportedBadge>,
    val rawEvents: List<ExportedRawEvent>,
    val fragments: List<ExportedFragment> = emptyList(),
    val milestones: List<ExportedMilestone> = emptyList()
)

/**
 * 导出 JSON 的解析与 schema 校验。
 *
 * 只负责把文本解析为强类型待导入条目，不触碰数据库；查重与入库由仓储层完成。
 * 版本不兼容、JSON 非法、字段缺失或类型不符均抛 [ImportException]。
 */
object ImportParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parse(text: String): ParsedImport {
        val root = try {
            MiniJson.parse(text)
        } catch (e: Exception) {
            throw ImportException("无法解析 JSON：${e.message ?: "格式错误"}")
        }.asObject() ?: throw ImportException("导出文件根节点必须是对象")

        val version = root.long("schemaVersion")
            ?: throw ImportException("缺少 schemaVersion 字段")
        if (version != SUPPORTED_SCHEMA_VERSION.toLong()) {
            throw ImportException("不支持的导出版本 $version（当前支持 $SUPPORTED_SCHEMA_VERSION）")
        }

        val memories = root.array("confirmedMemories").map { it.toMemory() }
        val tags = root.array("statusTags").map { it.toTag() }
        val narratives = root.optionalArray("narratives")?.map { it.toNarrative() } ?: emptyList()
        val scores = root.array("attributeScores").map { it.toScore() }
        val badges = root.array("badges").map { it.toBadge() }
        val rawEvents = root.optionalArray("rawEvents")?.map { it.toRawEvent() } ?: emptyList()
        val fragments = root.optionalArray("manualFragments")?.map { it.toFragment() } ?: emptyList()
        val milestones = root.optionalArray("milestones")?.map { it.toMilestone() } ?: emptyList()

        return ParsedImport(
            memories = memories,
            tags = tags,
            narratives = narratives,
            scores = scores,
            badges = badges,
            rawEvents = rawEvents,
            fragments = fragments,
            milestones = milestones
        )
    }

    private fun JsonValue.Obj.missing(field: String): Nothing =
        throw ImportException("字段缺失或类型错误：$field")

    private fun JsonValue.Obj.long(field: String): Long? =
        (map[field] as? JsonValue.Num)?.raw?.toLongOrNull()

    private fun JsonValue.Obj.array(field: String): List<JsonValue> =
        (map[field] as? JsonValue.Arr)?.items ?: missing(field)

    private fun JsonValue.Obj.optionalArray(field: String): List<JsonValue>? =
        (map[field] as? JsonValue.Arr)?.items

    private fun JsonValue.toMemory(): ExportedMemory {
        val o = asObject() ?: throw ImportException("记忆条目必须是对象")
        return ExportedMemory(
            id = o.long("id") ?: 0L,
            content = o.map["content"].asStringOrThrow("content"),
            periodStart = o.long("periodStart") ?: 0L,
            periodEnd = o.long("periodEnd") ?: 0L,
            createdAt = o.long("createdAt") ?: 0L
        )
    }

    private fun JsonValue.toTag(): ExportedTag {
        val o = asObject() ?: throw ImportException("标签条目必须是对象")
        return ExportedTag(
            id = o.long("id") ?: 0L,
            label = o.map["label"].asStringOrThrow("label"),
            createdAt = o.long("createdAt") ?: 0L,
            reviewedAt = o.long("reviewedAt")
        )
    }

    private fun JsonValue.toNarrative(): ExportedNarrative {
        val o = asObject() ?: throw ImportException("回顾文案条目必须是对象")
        return ExportedNarrative(
            id = o.long("id") ?: 0L,
            content = o.map["content"].asStringOrThrow("content"),
            createdAt = o.long("createdAt") ?: 0L,
            reviewedAt = o.long("reviewedAt")
        )
    }

    private fun JsonValue.toScore(): ExportedScore {
        val o = asObject() ?: throw ImportException("分值条目必须是对象")
        return ExportedScore(
            attributeKey = o.map["attributeKey"].asStringOrThrow("attributeKey"),
            periodStart = o.long("periodStart") ?: 0L,
            periodEnd = o.long("periodEnd") ?: 0L,
            score = o.map["score"].asIntOrThrow("score"),
            confidence = (o.map["confidence"] as? JsonValue.Num)?.raw?.toDoubleOrNull() ?: 0.0,
            evidenceJson = o.map["evidenceJson"].asStringOrThrow("evidenceJson"),
            sampleDays = o.map["sampleDays"].asIntOrThrow("sampleDays"),
            updatedAt = o.long("updatedAt") ?: 0L
        )
    }

    private fun JsonValue.toBadge(): ExportedBadge {
        val o = asObject() ?: throw ImportException("徽章条目必须是对象")
        return ExportedBadge(
            badgeKey = o.map["badgeKey"].asStringOrThrow("badgeKey"),
            unlockedAt = o.long("unlockedAt") ?: 0L,
            conditionSnapshotJson = o.map["conditionSnapshotJson"].asStringOrThrow("conditionSnapshotJson")
        )
    }

    private fun JsonValue.toRawEvent(): ExportedRawEvent {
        val o = asObject() ?: throw ImportException("原始事件条目必须是对象")
        return ExportedRawEvent(
            timestamp = o.long("timestamp") ?: 0L,
            eventType = o.map["eventType"].asStringOrThrow("eventType"),
            category = o.map["category"].asStringOrThrow("category"),
            durationMs = o.long("durationMs") ?: 0L,
            sessionId = o.map["sessionId"].asNullableString()
        )
    }

    private fun JsonValue.toFragment(): ExportedFragment {
        val o = asObject() ?: throw ImportException("片段条目必须是对象")
        return ExportedFragment(
            content = o.map["content"].asStringOrThrow("content"),
            mood = o.map["mood"].asNullableString(),
            category = o.map["category"].asNullableString(),
            anchorEpochMs = o.long("anchorEpochMs"),
            createdAt = o.long("createdAt") ?: 0L,
            updatedAt = o.long("updatedAt") ?: 0L
        )
    }

    private fun JsonValue.toMilestone(): ExportedMilestone {
        val o = asObject() ?: throw ImportException("里程碑条目必须是对象")
        return ExportedMilestone(
            title = o.map["title"].asStringOrThrow("title"),
            label = o.map["label"].asNullableString(),
            note = o.map["note"].asNullableString(),
            anchorEpochMs = o.long("anchorEpochMs"),
            createdAt = o.long("createdAt") ?: 0L,
            updatedAt = o.long("updatedAt") ?: 0L
        )
    }

    private fun JsonValue?.asStringOrThrow(field: String): String =
        (this as? JsonValue.Str)?.value ?: throw ImportException("字段缺失或类型错误：$field")

    private fun JsonValue?.asIntOrThrow(field: String): Int =
        (this as? JsonValue.Num)?.raw?.toIntOrNull() ?: throw ImportException("字段缺失或类型错误：$field")

    private fun JsonValue?.asNullableString(): String? = when (this) {
        is JsonValue.Str -> value
        JsonValue.Null -> null
        else -> null
    }
}
