package app.minepanel.data.repository

import app.minepanel.core.time.TimeUtils
import app.minepanel.data.dao.BadgeDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.rules.AttributeEngine
import app.minepanel.rules.AttributeResult
import app.minepanel.rules.BaselinePolicy
import app.minepanel.rules.BlockUsage
import app.minepanel.rules.DayUsage
import app.minepanel.rules.EngineInput
import app.minepanel.rules.PanelBadge
import app.minepanel.rules.PanelTimelineDay
import app.minepanel.rules.WeekScore

/**
 * 面板数据组装：按“本周（近 7 天）/ 上周（前 7 天）”两个窗口分别跑规则引擎，
 * 只读取聚合与块级元数据；不读包名、不触发模型、不回写数值。
 */
class PanelRepository(
    private val rawDao: RawUsageEventDao,
    private val aggregateDao: DailyAggregateDao,
    private val badgeDao: BadgeDao
) {

    suspend fun load(): PanelData {
        val now = System.currentTimeMillis()
        val todayStart = TimeUtils.startOfToday()
        val currentFrom = todayStart - SIX_DAYS
        val lastFrom = todayStart - THIRTEEN_DAYS
        val lastEnd = todayStart - SIX_DAYS

        val currentResults = evaluateWindow(currentFrom, now)
        val lastResults = evaluateWindow(lastFrom, lastEnd)
        val lastWeekHasData = lastResults.any { it.sampleDays > 0 } || lastRawCount(lastFrom, lastEnd) > 0

        val currentWeekScores = currentResults.map { it.toWeekScore() }
        val lastWeekScores = lastResults.map { it.toWeekScore() }
        val sampleDays = currentWeekScores.maxOfOrNull { it.sampleDays } ?: 0

        return PanelData(
            isBaseline = BaselinePolicy.isBaseline(sampleDays),
            currentWeekScores = currentWeekScores,
            lastWeekScores = lastWeekScores,
            lastWeekHasData = lastWeekHasData,
            timeline = timeline(lastFrom, now),
            badges = badgeDao.all().map { PanelBadge(it.badgeKey, it.unlockedAt) }
        )
    }

    private suspend fun evaluateWindow(from: Long, to: Long): List<AttributeResult> {
        if (to <= from) return emptyList()
        val events = rawDao.range(from, to)
        val blocks = events.map { BlockUsage(it.timestamp, it.durationMs, it.category) }
        val daily = aggregateDao.between(TimeUtils.dateKey(from), TimeUtils.dateKey(to))
        val days = daily.groupBy { it.date }.map { (date, rows) ->
            DayUsage(
                dateKey = date,
                categoryMs = rows.associate { it.category to it.foregroundMs },
                eventCount = rows.sumOf { it.eventCount },
                unlockCount = rows.maxOfOrNull { it.unlockCount } ?: 0,
                foregroundTotalMs = rows.sumOf { it.foregroundMs }
            )
        }
        return AttributeEngine.evaluate(EngineInput(days, blocks))
    }

    private suspend fun lastRawCount(from: Long, to: Long): Int =
        if (to <= from) 0 else rawDao.range(from, to).size

    private suspend fun timeline(from: Long, to: Long): List<PanelTimelineDay> {
        val rows = aggregateDao.between(TimeUtils.dateKey(from), TimeUtils.dateKey(to))
        return rows.groupBy { it.date }.map { (date, dayRows) ->
            PanelTimelineDay(
                dateKey = date,
                totalMs = dayRows.sumOf { it.foregroundMs },
                categoryMs = dayRows.associate { it.category to it.foregroundMs }
            )
        }.sortedBy { it.dateKey }
    }

    private fun AttributeResult.toWeekScore(): WeekScore =
        WeekScore(key, score, confidence, evidence, sampleDays)

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private val SIX_DAYS = 6 * DAY_MS
        private val THIRTEEN_DAYS = 13 * DAY_MS
    }
}

/** 面板快照：UI 直接按不可变状态渲染。 */
data class PanelData(
    val isBaseline: Boolean,
    val currentWeekScores: List<WeekScore>,
    val lastWeekScores: List<WeekScore>,
    val lastWeekHasData: Boolean,
    val timeline: List<PanelTimelineDay>,
    val badges: List<PanelBadge>
)
