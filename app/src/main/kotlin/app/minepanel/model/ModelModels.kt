package app.minepanel.model

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.ModelStatus

/**
 * 传给模型的精简输入：只含结论性聚合数值与粗类别分钟数。
 * 绝不包含原始事件流、应用包名、联系人或通知正文。
 */
data class ModelInput(
    val scores: Map<AttributeKey, Int>,
    val totalScreenTimeMin: Int,
    val sampleDays: Int,
    val categoryMinutes: Map<String, Int>,
    val weekOverWeek: Map<AttributeKey, Int?>,
    /** 长周期回顾窗标签（如「2026年11月」）；周回顾为 null。 */
    val periodLabel: String? = null,
    val memoryCount: Int = 0,
    val fragmentCount: Int = 0,
    /** 截断后的记忆/片段要点；不含正文全文与原始事件。 */
    val highlights: List<String> = emptyList()
) {
    /** 窗口内没有任何可用样本（天数/时长/记忆/片段/要点皆空），模型无内容可总结，应直接模板兜底。 */
    fun isEmptyWindow(): Boolean =
        sampleDays <= 0 && memoryCount <= 0 && fragmentCount <= 0 &&
            totalScreenTimeMin <= 0 && highlights.isEmpty()
}

/** 经 schema 校验后的模型输出草案；模型不得回写分值，也不得产出诊断结论。 */
data class ModelOutput(
    val panelNarrative: String,
    val statusTags: List<String>,
    val draftMemories: List<String>,
    val status: ModelStatus,
    val engineName: String
)

/** 推理引擎统一接口：可替换为模板降级或本地 GGUF；不允许任何联网实现。 */
interface InferenceEngine {
    val engineName: String
    suspend fun infer(input: ModelInput): ModelOutput

    /** 自由文本聊天：传入拼好的 ChatML 全文，返回模型原始回复；不做 JSON schema 解析。 */
    suspend fun chat(prompt: String): String
}
