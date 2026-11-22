package app.minepanel.core.time

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 长周期回顾口径：自然月 / 自然季 / 自然年。 */
enum class ReviewPeriod(val label: String) {
    MONTH("月"), QUARTER("季"), YEAR("年");

    companion object {
        fun fromName(name: String?): ReviewPeriod =
            entries.firstOrNull { it.name == name } ?: MONTH
    }
}

/** 一个半开的自然时间窗 [startMs, endMs)，附用于展示的中文标签。 */
data class ReviewWindow(
    val period: ReviewPeriod,
    val startMs: Long,
    val endMs: Long,
    val label: String
)

/**
 * 自然窗划分：对齐日历边界，不使用滚动近 N 天。
 * [stepsBack] 为往前回看的窗数：0 当前窗，1 上一窗，依此类推。
 */
object ReviewWindows {

    fun window(
        period: ReviewPeriod,
        stepsBack: Int = 0,
        now: LocalDate = LocalDate.now(TimeUtils.zone())
    ): ReviewWindow {
        val zone = TimeUtils.zone()
        return when (period) {
            ReviewPeriod.MONTH -> month(now, stepsBack, zone)
            ReviewPeriod.QUARTER -> quarter(now, stepsBack, zone)
            ReviewPeriod.YEAR -> year(now, stepsBack, zone)
        }
    }

    private fun month(now: LocalDate, stepsBack: Int, zone: ZoneId): ReviewWindow {
        val ym = YearMonth.from(now).minusMonths(stepsBack.toLong())
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val label = "${ym.year}年${ym.monthValue}月"
        return ReviewWindow(ReviewPeriod.MONTH, start, end, label)
    }

    private fun quarter(now: LocalDate, stepsBack: Int, zone: ZoneId): ReviewWindow {
        // 先把月份回退 stepsBack 个季度（每季 3 个月），再对齐到该月所在季度首月。
        val base = YearMonth.from(now).minusMonths(stepsBack * 3L)
        val quarterIndex = (base.monthValue - 1) / 3
        val firstMonth = quarterIndex * 3 + 1
        val startYm = YearMonth.of(base.year, firstMonth)
        val start = startYm.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = startYm.plusMonths(3).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val label = "${base.year}年第${quarterIndex + 1}季度"
        return ReviewWindow(ReviewPeriod.QUARTER, start, end, label)
    }

    private fun year(now: LocalDate, stepsBack: Int, zone: ZoneId): ReviewWindow {
        val year = now.year - stepsBack
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val label = "${year}年"
        return ReviewWindow(ReviewPeriod.YEAR, start, end, label)
    }
}
