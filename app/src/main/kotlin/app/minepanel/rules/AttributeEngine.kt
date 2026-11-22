package app.minepanel.rules

import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.EventCategory
import app.minepanel.core.time.TimeUtils
import kotlin.math.sqrt

/**
 * 六维规则引擎：所有数值（分值、置信度）只在此层计算，模型不回写数值。
 * 输入为聚合/块级元数据；输出分值 0–100、置信度 0–1 与人话依据摘要。
 */
object AttributeEngine {

    fun evaluate(input: EngineInput): List<AttributeResult> {
        val sampleDays = input.days.size
        val baseConfidence = when {
            sampleDays == 0 -> 0.05
            else -> (sampleDays / BaselinePolicy.BASELINE_DAYS.toDouble()).coerceIn(0.15, 1.0)
        }
        return listOf(
            focus(input, sampleDays, baseConfidence),
            regularity(input, sampleDays, baseConfidence),
            vitality(input, sampleDays, baseConfidence),
            social(input, sampleDays, baseConfidence),
            exploration(sampleDays, baseConfidence),
            moderation(input, sampleDays, baseConfidence)
        )
    }

    // ---------- 专注 ----------
    private fun focus(input: EngineInput, sampleDays: Int, confidence: Double): AttributeResult {
        val blocks = input.blocks
        val totalMs = blocks.sumOf { it.durationMs }
        val maxBlockMs = blocks.maxOfOrNull { it.durationMs } ?: 0L
        val switches = (blocks.size - 1).coerceAtLeast(0)
        val perHour = if (totalMs > 0) switches.toDouble() / (totalMs / 3600_000.0) else 0.0

        val lengthScore = linear(maxBlockMs.toDouble(), 5 * 60_000.0, 40 * 60_000.0, 20.0, 60.0)
        val fragmentationScore = 100.0 - linear(perHour, 0.0, 8.0, 0.0, 50.0)
        val score = clamp((lengthScore + fragmentationScore) / 2)

        val evidence = listOf(
            "最长连续使用 ${minutes(maxBlockMs)} 分钟",
            "共 ${blocks.size} 个前台块，约 $switches 次切换"
        )
        return AttributeResult(AttributeKey.FOCUS, score, confidence, evidence, sampleDays)
    }

    // ---------- 规律 ----------
    private fun regularity(input: EngineInput, sampleDays: Int, confidence: Double): AttributeResult {
        val firstHourByDay = input.blocks
            .groupBy { TimeUtils.dateKey(it.startMs) }
            .map { entry ->
                val firstMs = entry.value.minOf { it.startMs }
                InstantHour.of(firstMs).hourOfDay
            }
        val totals = input.days.map { it.foregroundTotalMs.toDouble() }

        val hourStd = stdDev(firstHourByDay.map { it.toDouble() })
        val totalCv = if (totals.size > 1 && totals.average() > 0) {
            stdDev(totals) / totals.average()
        } else 0.0

        val hourScore = 100.0 - linear(hourStd, 0.0, 3.0, 0.0, 50.0)
        val stabilityScore = 100.0 - linear(totalCv, 0.0, 1.0, 0.0, 50.0)
        val score = clamp((hourScore + stabilityScore) / 2)

        val evidence = buildList {
            add("每日首次使用时间波动约 ${String.format("%.1f", hourStd)} 小时")
            add("日间使用总量差异系数 ${String.format("%.2f", totalCv)}")
        }
        return AttributeResult(AttributeKey.REGULARITY, score, confidence, evidence, sampleDays)
    }

    // ---------- 活力 ----------
    private fun vitality(input: EngineInput, sampleDays: Int, confidence: Double): AttributeResult {
        val blocks = input.blocks
        val totalMs = blocks.sumOf { it.durationMs }
        val daytimeMs = blocks.filter { InstantHour.of(it.startMs).isDaytime }.sumOf { it.durationMs }
        val sedentaryCount = blocks.count { it.durationMs >= SEDENTARY_BLOCK_MS }
        val daytimeRatio = if (totalMs > 0) daytimeMs.toDouble() / totalMs else 0.0

        val daytimeScore = linear(daytimeRatio, 0.3, 0.9, 30.0, 70.0)
        val sedentaryPenalty = linear(sedentaryCount.toDouble(), 0.0, 5.0, 0.0, 30.0)
        val score = clamp(daytimeScore - sedentaryPenalty)

        val evidence = buildList {
            add("白天时段使用占比 ${String.format("%.0f", daytimeRatio * 100)}%")
            add("超长久坐块 $sedentaryCount 段")
            add("步数来自 Health Connect 插件，当前未启用" )
        }
        return AttributeResult(AttributeKey.VITALITY, score, confidence, evidence, sampleDays)
    }

    // ---------- 社交 ----------
    private fun social(input: EngineInput, sampleDays: Int, confidence: Double): AttributeResult {
        val totalMs = input.blocks.sumOf { it.durationMs }
        val socialMs = input.blocks
            .filter { it.category == EventCategory.SOCIAL.name }
            .sumOf { it.durationMs }
        val ratio = if (totalMs > 0) socialMs.toDouble() / totalMs else 0.0
        val dailySocial = if (sampleDays > 0) socialMs / sampleDays else 0L

        // 社交维度只描述事实，不做优劣评判；分值取中性基线加轻微节律微调。
        val score = clamp(50.0 + (0.5 - ratio) * 20.0)
        val evidence = listOf(
            "社交类别日均 ${minutes(dailySocial)} 分钟",
            "占总使用时长 ${String.format("%.0f", ratio * 100)}%",
            "通话频次/时长来自通话日志插件，当前未启用"
        )
        return AttributeResult(AttributeKey.SOCIAL, score, confidence, evidence, sampleDays)
    }

    // ---------- 探索（插件未启用，低置信度中性值） ----------
    private fun exploration(sampleDays: Int, baseConfidence: Double): AttributeResult {
        return AttributeResult(
            key = AttributeKey.EXPLORATION,
            score = 50,
            confidence = (baseConfidence * 0.2).coerceAtLeast(0.1),
            evidence = listOf("外出/日历插件未启用，暂无探索维度依据"),
            sampleDays = sampleDays
        )
    }

    // ---------- 数字节制 ----------
    private fun moderation(input: EngineInput, sampleDays: Int, confidence: Double): AttributeResult {
        val blocks = input.blocks
        val totalMs = blocks.sumOf { it.durationMs }
        val nightMs = blocks.filter { InstantHour.of(it.startMs).isNight }.sumOf { it.durationMs }
        val nightRatio = if (totalMs > 0) nightMs.toDouble() / totalMs else 0.0
        val longest = blocks.maxOfOrNull { it.durationMs } ?: 0L

        val nightScore = 100.0 - linear(nightRatio, 0.0, 0.4, 0.0, 60.0)
        val longBlockPenalty = linear(longest.toDouble(), 20 * 60_000.0, 90 * 60_000.0, 0.0, 20.0)
        val score = clamp(nightScore - longBlockPenalty)

        val evidence = listOf(
            "深夜（23:00–06:00）使用占比 ${String.format("%.0f", nightRatio * 100)}%",
            "单次最长使用 ${minutes(longest)} 分钟"
        )
        return AttributeResult(AttributeKey.DIGITAL_MODERATION, score, confidence, evidence, sampleDays)
    }

    // ---------- 辅助 ----------

    private fun linear(value: Double, inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double {
        if (inMax <= inMin) return outMin
        val t = ((value - inMin) / (inMax - inMin)).coerceIn(0.0, 1.0)
        return outMin + t * (outMax - outMin)
    }

    private fun clamp(value: Double): Int = Math.round(value).toInt().coerceIn(0, 100)

    private fun minutes(ms: Long): Long = ms / 60_000

    private fun stdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private data class InstantHour(val hourOfDay: Int) {
        val isDaytime: Boolean get() = hourOfDay in 6..22
        val isNight: Boolean get() = hourOfDay < 6 || hourOfDay >= 23

        companion object {
            fun of(epochMs: Long): InstantHour =
                InstantHour(java.time.Instant.ofEpochMilli(epochMs).atZone(TimeUtils.zone()).hour)
        }
    }

    private const val SEDENTARY_BLOCK_MS = 45 * 60_000L
}
