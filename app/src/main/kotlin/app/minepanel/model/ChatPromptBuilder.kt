package app.minepanel.model

import app.minepanel.core.model.ChatRole
import kotlin.math.ceil

/** 一轮聊天消息：角色与正文，供提示词装配使用。 */
data class ChatTurn(val role: ChatRole, val content: String)

/**
 * 聊天提示词构造：0.5B 原生上下文仅 2048 token，因此按 token 估算装配，并预留输出、
 * 系统提示、对话标记与安全余量；历史只在完整轮次边界截断，不在一句话或特殊标记中间切断。
 *
 * 与回顾链路不同，聊天不要求固定 JSON 输出，只拼 Qwen2.5 ChatML，由模型续写自然回复。
 * 已采纳的长期记忆作为背景要点附在 system 段，不堆整段历史。
 */
object ChatPromptBuilder {

    private const val SYSTEM_PROMPT =
        "你是本地设备上的简短闲聊助手，只依据当前对话上下文回答。" +
            "语气中性简短；不做健康、心理或人格诊断；不提及具体应用名称；" +
            "不编造使用数据，不清楚就如实说明。"

    private const val EXTRACTION_SYSTEM =
        "你是本地对话要点整理助手。只提取本人明确表达的偏好、事实、约定或长期有效的信息，写成简短中文条目。" +
            "每条不超过 40 字；只记长期有效内容，不记随口一问、情绪或临时话题；" +
            "不做健康、心理或人格诊断；不提及应用名称；不编造对话里没有的信息；" +
            "不复述对话全文，不输出分值、字段名或 JSON 结构；没有可沉淀的要点就返回空数组。" +
            "只返回 JSON：{\"memories\":[\"要点\"]}。"

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    /** 原生上下文窗口 token 数（与 cpp 侧 DEFAULT_CONTEXT_SIZE 对齐）。 */
    private const val CONTEXT_WINDOW = 2048

    /** 预留单次回复的输出 token。 */
    private const val RESERVED_OUTPUT_TOKENS = 256

    /** 预留系统提示与 ChatML 标记的固定开销。 */
    private const val RESERVED_SYSTEM_TOKENS = 180

    /** 安全余量：估算偏差、标点与换行波动。 */
    private const val RESERVED_SAFETY_TOKENS = 96

    /** 长期记忆背景可占用的 token 预算。 */
    private const val MEMORY_TOKEN_BUDGET = 256

    /** 历史对话可占用的 token 预算：窗口扣掉输出、系统、记忆与安全余量。 */
    private val HISTORY_TOKEN_BUDGET =
        CONTEXT_WINDOW - RESERVED_OUTPUT_TOKENS - RESERVED_SYSTEM_TOKENS -
            MEMORY_TOKEN_BUDGET - RESERVED_SAFETY_TOKENS

    /** 历史最多保留的消息条数（用户/助手合计），超出先丢最老。 */
    private const val MAX_HISTORY_MESSAGES = 12

    /** 注入背景的长期记忆条数上限。 */
    private const val MAX_MEMORY_ITEMS = 10

    /** 提炼要点时回放给模型的历史 token 预算。 */
    private const val EXTRACTION_HISTORY_TOKEN_BUDGET = 1000

    /** 组装 system 段文本：基础助手约束 + 已采纳长期记忆背景；供本地 ChatML 与远端 messages 复用。 */
    fun buildSystem(memories: List<String> = emptyList()): String {
        val items = memories.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_MEMORY_ITEMS)
        return buildString {
            append(SYSTEM_PROMPT)
            if (items.isNotEmpty()) {
                append("\n已知背景（本人确认过的要点，相关时可参考，不要原样复述）：")
                items.forEach { append("\n- ").append(it) }
            }
        }
    }

    /** 装配闲聊提示词：[memories] 为已采纳的长期记忆要点，作为背景附在 system 段。 */
    fun buildChat(messages: List<ChatTurn>, memories: List<String> = emptyList()): String {
        val memoryItems = selectMemories(memories)
        val history = recentWithinBudget(messages, HISTORY_TOKEN_BUDGET)
        return buildString {
            append(IM_START).append("system\n").append(SYSTEM_PROMPT)
            if (memoryItems.isNotEmpty()) {
                append("\n已知背景（本人确认过的要点，相关时可参考，不要原样复述）：")
                memoryItems.forEach { append("\n- ").append(it) }
            }
            append(IM_END).append('\n')
            history.forEach { turn ->
                appendTurn(this, turn)
            }
            append(IM_START).append("assistant\n")
        }
    }

    /** 装配要点提炼提示词：回放最近一段对话，要求模型输出 memories JSON。 */
    fun buildMemoryExtraction(turns: List<ChatTurn>): String {
        val history = recentWithinBudget(turns, EXTRACTION_HISTORY_TOKEN_BUDGET)
        return buildString {
            append(IM_START).append("system\n").append(EXTRACTION_SYSTEM).append(IM_END).append('\n')
            append(IM_START).append("user\n对话：\n")
            history.forEach { turn ->
                val label = if (turn.role == ChatRole.ASSISTANT) "对方" else "我"
                append(label).append("：").append(turn.content).append('\n')
            }
            append(IM_END).append('\n')
            append(IM_START).append("assistant\n")
        }
    }

    private fun appendTurn(builder: StringBuilder, turn: ChatTurn) {
        val roleName = if (turn.role == ChatRole.ASSISTANT) "assistant" else "user"
        builder.append(IM_START).append(roleName).append('\n')
            .append(turn.content).append(IM_END).append('\n')
    }

    /** 筛选背景记忆：去空去重，按 token 预算与条数上限取最近之前的条目。 */
    private fun selectMemories(memories: List<String>): List<String> {
        val cleaned = memories.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val selected = mutableListOf<String>()
        var used = 0
        for (item in cleaned) {
            if (selected.size >= MAX_MEMORY_ITEMS) break
            val cost = estimateTokens(item)
            if (selected.isNotEmpty() && used + cost > MEMORY_TOKEN_BUDGET) break
            selected.add(item)
            used += cost
        }
        return selected
    }

    /** 取最近 [MAX_HISTORY_MESSAGES] 条，再按 token 预算从最新往前装；整轮边界截断，至少保留最后一条。 */
    private fun recentWithinBudget(messages: List<ChatTurn>, budget: Int): List<ChatTurn> {
        if (messages.isEmpty()) return messages
        val recent = messages.takeLast(MAX_HISTORY_MESSAGES)
        val selected = mutableListOf<ChatTurn>()
        var used = 0
        for (turn in recent.asReversed()) {
            val cost = estimateTokens(turn.content)
            if (selected.isNotEmpty() && used + cost > budget) break
            selected.add(0, turn)
            used += cost
        }
        return selected
    }

    /**
     * 粗估 token 数：CJK/全角字符按 1 token，字母数字按 0.35，空白按 0.25，其余符号按 0.5。
     * 只用于预算截断，不要求与分词器精确一致。
     */
    internal fun estimateTokens(text: String): Int {
        var tokens = 0.0
        text.forEach { ch ->
            tokens += when {
                isCjk(ch) -> 1.0
                ch.isWhitespace() -> 0.25
                ch.isLetterOrDigit() -> 0.35
                else -> 0.5
            }
        }
        return ceil(tokens).toInt().coerceAtLeast(1)
    }

    private fun isCjk(ch: Char): Boolean = ch.code in 0x2E80..0x9FFF ||
        ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF
}
