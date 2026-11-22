package app.minepanel.collector

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.ServiceLocator

/** 周期性采集 Worker：后台拉取自上次同步以来的使用事件并落库。 */
class UsageCollectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val collector = UsageCollector(
                context = applicationContext,
                dao = ServiceLocator.database().rawUsageEventDao(),
                settings = ServiceLocator.settings()
            )
            collector.collect()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "usage_collect"
    }
}
