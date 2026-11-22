package app.minepanel.model

/**
 * Prompt 构造器：0.5B 模型上下文小、易读不懂长输入，因此只给结论性数值，
 * 绝不下发整日应用切换流或原始事件。数据片段为单行紧凑 JSON。
 *
 * GGUF 引擎走 [buildChatML]：在 Kotlin 侧拼好 Qwen2.5 标准 ChatML（system + user），
 * 底层不再二次套模板，避免双重格式化破坏特殊标记。
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT =
        "你是本地设备使用回顾助手，只依据给定的粗类别元数据。" +
            "用自然中文写一句简短回顾；不要复述输入里的数据，不要输出分值、字段名、键名或 JSON 结构；" +
            "不做健康、心理或人格诊断；不提及具体应用名称。" +
            "只返回 JSON：{\"panelNarrative\":\"回顾一句话\",\"statusTags\":[\"一个标签\"]," +
            "\"draftMemories\":[\"一个要点\"]}。"

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    /** 用户段：输出格式约束已在 system 给出，这里只附结论性数据 JSON。 */
    fun userPrompt(input: ModelInput): String = "数据：" + compactJson(input)

    /** Qwen2.5 ChatML 全文；assistant 头留空，由模型续写。 */
    fun buildChatML(input: ModelInput): String = buildString {
        append(IM_START).append("system\n").append(SYSTEM_PROMPT).append(IM_END).append('\n')
        append(IM_START).append("user\n").append(userPrompt(input)).append(IM_END).append('\n')
        append(IM_START).append("assistant\n")
    }

    /** 纯文本版本，保留用于对照与调试；GGUF 推理统一使用 [buildChatML]。 */
    fun build(input: ModelInput): String = buildString {
        appendLine(SYSTEM_PROMPT)
        append("数据：").append(compactJson(input))
    }

    internal fun compactJson(input: ModelInput): String {
        val scores = input.scores.entries.joinToString(",") { (k, v) -> "\"${k.name}\":$v" }
        val categories = input.categoryMinutes.entries.joinToString(",") { (k, v) -> "\"$k\":$v" }
        val highlights = input.highlights.joinToString(",") { "\"${escapeJson(it)}\"" }
        val period = input.periodLabel?.let { ",\"period\":\"${escapeJson(it)}\"" } ?: ""
        return "{\"scores\":{$scores}," +
            "\"total_screen_time_min\":${input.totalScreenTimeMin}," +
            "\"sample_days\":${input.sampleDays}," +
            "\"categories\":{$categories}" + period +
            ",\"memory_count\":${input.memoryCount}," +
            "\"fragment_count\":${input.fragmentCount}," +
            "\"highlights\":[$highlights]}"
    }

    private fun escapeJson(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")
}
