package app.minepanel.aggregate

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.ServiceLocator
import app.minepanel.data.repository.UsageRepository

/** 聚合 Worker：重算最近一段时间的日聚合，供规则引擎读取。 */
class AggregateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = ServiceLocator.database()
            val to = System.currentTimeMillis()
            val from = to - LOOKBACK_MS
            val events = db.rawUsageEventDao().range(from, to)
            DailyAggregator(db.dailyAggregateDao()).aggregate(events, from, to)
            UsageRepository(
                rawDao = db.rawUsageEventDao(),
                aggregateDao = db.dailyAggregateDao(),
                scoreDao = db.attributeScoreDao()
            ).recomputeScores()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "daily_aggregate"
        private const val LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000
    }
}
