package app.minepanel.rules

import app.minepanel.core.model.AttributeKey

/** 单个维度在某一周窗口的展示分值；数值来自规则引擎，模型不回写。 */
data class WeekScore(
    val key: AttributeKey,
    val score: Int,
    val confidence: Double,
    val evidence: List<String>,
    val sampleDays: Int
)

/** 时间轴上某一天的粗类别汇总（只含粗类别，不含包名）。 */
data class PanelTimelineDay(
    val dateKey: String,
    val totalMs: Long,
    val categoryMs: Map<String, Long>
)

/** 已解锁徽章的展示快照。 */
data class PanelBadge(
    val badgeKey: String,
    val unlockedAt: Long
)

/** 等级与经验进度：均分 0–100 映射到 Lv1–10，纯本地展示规则，不代表任何诊断结论。 */
data class LevelProgress(val level: Int, val progress: Float)

/** 本周/上周同一维度的配对结果。 */
data class DimensionPair(
    val key: AttributeKey,
    val current: WeekScore?,
    val last: WeekScore?
) {
    /** 本周相对上周的分值变化；任一周缺数据时为 null。 */
    val delta: Int? = if (current != null && last != null) current.score - last.score else null
}

object PanelScoring {

    const val MAX_LEVEL = 10

    /** 均分 0–100 → Lv1–10；每 10 分一级，progress 为当前级内进度。 */
    fun levelOf(average: Int): LevelProgress {
        val clamped = average.coerceIn(0, 100)
        val level = (clamped / 10).coerceIn(1, MAX_LEVEL)
        val progress = if (level >= MAX_LEVEL) 1f else ((clamped % 10) / 10f)
        return LevelProgress(level, progress)
    }

    fun average(scores: List<WeekScore>): Int =
        if (scores.isEmpty()) 0 else Math.round(scores.map { it.score }.average()).toInt()

    /** 按六维固定顺序对齐本周/上周；上周缺维度时 [DimensionPair.last] 为 null。 */
    fun pair(current: List<WeekScore>, last: List<WeekScore>): List<DimensionPair> {
        val lastByKey = last.associateBy { it.key }
        return AttributeKey.entries.map { key ->
            DimensionPair(
                key = key,
                current = current.firstOrNull { it.key == key },
                last = lastByKey[key]
            )
        }
    }
}
