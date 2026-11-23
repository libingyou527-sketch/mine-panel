package app.minepanel.model

/** 解析并校验通过的模型草案。 */
data class ParsedOutput(
    val narrative: String,
    val tags: List<String>,
    val memories: List<String>
)

/**
 * 模型输出解析与校验：
 * - 小模型常输出 ```json ... ``` 代码块，先剥离围栏与反引号；
 * - 只接受固定 schema 的回顾/标签/记忆；
 * - 拒绝模型回写数值字段（score/confidence/level 等）；
 * - 拒绝诊断性措辞；拒绝正文回吐输入数据字典、schema 字段名或示例占位词；
 * - 校验不通过返回 null，由引擎层走降级或记错误。
 */
object ModelOutputParser {

    private const val MAX_TAGS = 6
    private const val MAX_MEMORIES = 6

    private val DIAGNOSTIC_WORDS = listOf(
        "诊断", "抑郁症", "抑郁", "焦虑症", "焦虑", "强迫症", "人格障碍",
        "精神病", "确诊", "病症", "病患", "多动症", "成瘾", "心理疾病"
    )

    private val NUMERIC_KEYS = listOf("score", "scores", "confidence", "level", "rank", "value")

    /** 输入数据 JSON 里可能被模型回吐的键名（snake_case 与 camelCase 都覆盖）。 */
    private val DATA_KEYS = listOf(
        "scores", "total_screen_time_min", "sample_days", "categories",
        "memory_count", "fragment_count", "highlights", "week_over_week",
        "totalScreenTimeMin", "sampleDays", "categoryMinutes", "memoryCount",
        "fragmentCount", "weekOverWeek"
    )

    /** 输出 schema 字段名；模型把字段名当文案回填时判不合格。 */
    private val FIELD_NAMES = listOf(
        "panelNarrative", "statusTags", "draftMemories", "narrative", "tags", "memories"
    )

    /** Prompt 里的示例占位词；被模型原样抄成输出时判不合格。 */
    private val PLACEHOLDER_TEXTS = setOf(
        "一句回顾", "标签", "记忆要点", "回顾一句话", "一个标签", "一个要点"
    )

    fun parse(raw: String?): ParsedOutput? {
        if (raw.isNullOrBlank()) return null
        val json = extractJson(raw) ?: return null
        if (containsNumericKey(json)) return null

        val narrative = stringField(json, "panelNarrative", "narrative") ?: return null
        if (!isHumanReadable(narrative)) return null

        val tags = arrayField(json, "statusTags", "tags").filter { isHumanReadable(it) }
        val memories = arrayField(json, "draftMemories", "memories").filter { isHumanReadable(it) }

        if (narrative.isBlank()) return null
        val allText = listOf(narrative) + tags + memories
        if (allText.any { hitsDiagnostic(it) }) return null

        return ParsedOutput(
            narrative = narrative.trim(),
            tags = tags.distinct().take(MAX_TAGS),
            memories = memories.distinct().take(MAX_MEMORIES)
        )
    }

    /** 解析要点提炼输出：只返回通过人读校验与诊断词过滤的简短条目；不合格返回空列表。 */
    fun parseMemoryItems(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val json = extractJson(raw) ?: return emptyList()
        if (containsNumericKey(json)) return emptyList()
        return arrayField(json, "memories", "draftMemories")
            .filter { isHumanReadable(it) && !hitsDiagnostic(it) }
            .distinct()
            .take(MAX_MEMORIES)
    }

    /** 正文必须是自然中文：不得回吐数据字典/键值结构、字段名或示例占位词。 */
    private fun isHumanReadable(text: String): Boolean {
        val t = text.trim()
        if (t.isBlank()) return false
        if (t in PLACEHOLDER_TEXTS) return false
        if (t.contains('<') || t.contains('>')) return false
        if (t.contains('{') || t.contains('}')) return false
        if (DATA_KEYS.any { t.contains(it) }) return false
        if (FIELD_NAMES.any { t.contains(it) }) return false
        // snake_case 键值结构，如 total_screen_time_min:0
        if (Regex("[a-z]+(_[a-z]+)+\\s*:").containsMatchIn(t)) return false
        val stripped = t.replace(Regex("[\\s\\p{Punct}]"), "")
        return stripped.length >= 2
    }

    /** 剥离 Markdown 代码块围栏，截取首个 { 到末个 } 的 JSON 片段。 */
    internal fun extractJson(raw: String): String? {
        var s = raw.trim()
        if (s.contains("```")) {
            s = s.replace(Regex("```(?:json|JSON)?"), "").trim()
        }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return s.substring(start, end + 1)
    }

    private fun containsNumericKey(json: String): Boolean {
        return NUMERIC_KEYS.any { Regex("\"?$it\"?\\s*:").containsMatchIn(json) }
    }

    private fun hitsDiagnostic(text: String): Boolean =
        DIAGNOSTIC_WORDS.any { text.contains(it) }

    private fun stringField(json: String, vararg names: String): String? {
        for (name in names) {
            val regex = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            val match = regex.find(json) ?: continue
            return decode(match.groupValues[1])
        }
        return null
    }

    private fun arrayField(json: String, vararg names: String): List<String> {
        for (name in names) {
            val arrayRegex = Regex("\"$name\"\\s*:\\s*\\[([^]]*)]")
            val arrayMatch = arrayRegex.find(json) ?: continue
            val content = arrayMatch.groupValues[1]
            val itemRegex = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
            return itemRegex.findAll(content).map { decode(it.groupValues[1].trim()) }
                .filter { it.isNotBlank() }
                .toList()
        }
        return emptyList()
    }

    private fun decode(escaped: String): String = escaped
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", " ")
        .replace("\\t", " ")
}
