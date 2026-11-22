package app.minepanel.aggregate

import app.minepanel.core.time.TimeUtils
import app.minepanel.data.dao.DailyAggregateDao
import app.minepanel.data.db.entity.DailyAggregate

/**
 * 日聚合：把原始前台块按本地自然日切分，汇总到“日期 + 粗类别”。
 * 重算指定范围前先清旧聚合，避免重复叠加；跨天块在午夜边界拆开计入两天。
 */
class DailyAggregator(private val dao: DailyAggregateDao) {

    suspend fun aggregate(
        events: List<app.minepanel.data.db.entity.RawUsageEvent>,
        fromMs: Long,
        toMs: Long
    ) {
        val fromDate = TimeUtils.dateKey(fromMs)
        val toDate = TimeUtils.dateKey(toMs)
        dao.deleteBetween(fromDate, toDate)

        val accumulators = mutableMapOf<Pair<String, String>, Accumulator>()
        for (event in events) {
            val segments = TimeUtils.splitByDay(event.timestamp, event.timestamp + event.durationMs)
            for (segment in segments) {
                val key = segment.dateKey to event.category
                val acc = accumulators.getOrPut(key) { Accumulator() }
                acc.foregroundMs += segment.ms
                acc.eventCount += 1
            }
        }

        val now = System.currentTimeMillis()
        accumulators.forEach { (key, acc) ->
            dao.upsert(
                DailyAggregate(
                    date = key.first,
                    category = key.second,
                    foregroundMs = acc.foregroundMs,
                    eventCount = acc.eventCount,
                    unlockCount = 0,
                    sleepWindowStart = null,
                    sleepWindowEnd = null,
                    updatedAt = now
                )
            )
        }
    }

    private class Accumulator {
        var foregroundMs: Long = 0
        var eventCount: Int = 0
    }
}
