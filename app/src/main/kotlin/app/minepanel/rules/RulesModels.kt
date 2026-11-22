package app.minepanel.rules

import app.minepanel.core.model.AttributeKey

/** 某日按粗类别汇总的使用情况（已聚合，不含包名）。 */
data class DayUsage(
    val dateKey: String,
    val categoryMs: Map<String, Long>,
    val eventCount: Int,
    val unlockCount: Int,
    val foregroundTotalMs: Long
)

/** 一个前台块（用于专注、节制等维度计算）。 */
data class BlockUsage(
    val startMs: Long,
    val durationMs: Long,
    val category: String
)

/** 规则引擎输入：只含聚合/块级元数据，不含原始包名。 */
data class EngineInput(
    val days: List<DayUsage>,
    val blocks: List<BlockUsage>
)

/** 单个属性的计算结果：分值 0–100、置信度 0–1、人话依据与样本天数。 */
data class AttributeResult(
    val key: AttributeKey,
    val score: Int,
    val confidence: Double,
    val evidence: List<String>,
    val sampleDays: Int
)
