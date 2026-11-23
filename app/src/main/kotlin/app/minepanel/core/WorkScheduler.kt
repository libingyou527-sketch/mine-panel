package app.minepanel.core

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.minepanel.aggregate.AggregateWorker
import app.minepanel.aggregate.InferenceWorker
import app.minepanel.collector.CalendarCollectWorker
import app.minepanel.collector.CallLogCollectWorker
import app.minepanel.collector.GeofenceCollectWorker
import app.minepanel.collector.UsageCollectWorker
import java.util.concurrent.TimeUnit

/** 本地后台任务调度：采集与聚合均在设备本地执行，不涉及网络。 */
object WorkScheduler {

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)

        val collectRequest = PeriodicWorkRequestBuilder<UsageCollectWorker>(15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UsageCollectWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            collectRequest
        )

        val aggregateRequest = PeriodicWorkRequestBuilder<AggregateWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            AggregateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            aggregateRequest
        )

        val inferenceRequest = PeriodicWorkRequestBuilder<InferenceWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            InferenceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            inferenceRequest
        )

        val calendarRequest = PeriodicWorkRequestBuilder<CalendarCollectWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CalendarCollectWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            calendarRequest
        )

        val callLogRequest = PeriodicWorkRequestBuilder<CallLogCollectWorker>(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CallLogCollectWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            callLogRequest
        )

        val geofenceRequest = PeriodicWorkRequestBuilder<GeofenceCollectWorker>(30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            GeofenceCollectWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            geofenceRequest
        )
    }

    /** 手动触发一次：先重算聚合，再跑本地推理，生成草案入队。全程本地。 */
    fun runOnce(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        val aggregate = OneTimeWorkRequestBuilder<AggregateWorker>().addTag(MANUAL_TAG).build()
        val inference = OneTimeWorkRequestBuilder<InferenceWorker>().addTag(MANUAL_TAG).build()
        workManager.beginWith(aggregate).then(inference).enqueue()
    }

    const val MANUAL_TAG = "manual_run"
}
