package app.minepanel.data.repository

import app.minepanel.data.dao.AttributeScoreDao
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.data.db.entity.AttributeScore
import app.minepanel.rules.AttributeEngine
import app.minepanel.rules.BlockUsage
import app.minepanel.rules.DayUsage
import app.minepanel.rules.EngineInput
import app.minepanel.rules.AttributeResult
import app.minepanel.core.time.TimeUtils

/**
 * 组装规则引擎输入并落库属性分值。
 * 证据摘要以分隔符编码存库（阶段 4 引入 JSON 序列化后再迁移）；不含包名。
 */
class UsageRepository(
    private val rawDao: RawUsageEventDao,
    private val aggregateDao: DailyAggregateDao,
    private val scoreDao: AttributeScoreDao
) {

    suspend fun recomputeScores(): List<AttributeResult> {
        val to = System.currentTimeMillis()
        val from = to - LOOKBACK_MS

        val events = rawDao.range(from, to)
        val blocks = events.map { BlockUsage(it.timestamp, it.durationMs, it.category) }

        val daily = aggregateDao.between(TimeUtils.dateKey(from), TimeUtils.dateKey(to))
        val days = daily.map {
            DayUsage(
                dateKey = it.date,
                categoryMs = mapOf(it.category to it.foregroundMs),
                eventCount = it.eventCount,
                unlockCount = it.unlockCount,
                foregroundTotalMs = it.foregroundMs
            )
        }

        val results = AttributeEngine.evaluate(EngineInput(days, blocks))
        val now = System.currentTimeMillis()
        results.forEach { result ->
            scoreDao.upsert(
                AttributeScore(
                    attributeKey = result.key.name,
                    periodStart = from,
                    periodEnd = to,
                    score = result.score,
                    confidence = result.confidence,
                    evidenceJson = result.evidence.joinToString(EVIDENCE_SEPARATOR),
                    sampleDays = result.sampleDays,
                    updatedAt = now
                )
            )
        }
        return results
    }

    companion object {
        private const val LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000
        const val EVIDENCE_SEPARATOR = "¶"
    }
}
