package app.minepanel.collector

import android.Manifest
import android.content.ContentUris
import android.provider.CalendarContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.PermissionUtils
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import app.minepanel.data.repository.PluginEventRepository
import kotlinx.coroutines.flow.first

/**
 * 日历插件采集：只读事件开始/结束时间，用于计划与实际对照。
 * 不读取标题、地点、参与者、描述；未授权或插件关闭时静默跳过。
 */
class CalendarCollectWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val settings = ServiceLocator.settings()
            val enabled = settings.settings.first().plugins[PluginType.CALENDAR] ?: false
            if (!enabled || !PermissionUtils.hasPermission(applicationContext, Manifest.permission.READ_CALENDAR)) {
                return Result.success()
            }

            val since = System.currentTimeMillis() - LOOKBACK_MS
            val repo = PluginEventRepository(ServiceLocator.database().pluginEventDao())
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND
            )
            applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events.DTSTART} >= ?",
                arrayOf(since.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val start = cursor.getLong(1)
                    val end = cursor.getLong(2).takeIf { it > 0 }
                    val eventKey = ContentUris.appendId(
                        android.net.Uri.Builder(), cursor.getLong(0)
                    ).build().lastPathSegment
                    val payload = buildString {
                        append("{\"start\":").append(start)
                        if (end != null) append(",\"end\":").append(end)
                        append(",\"eventRef\":").append(PluginJson.escape(eventKey.orEmpty()))
                        append('}')
                    }
                    repo.record(PluginType.CALENDAR, "calendar_event", payload)
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val WORK_NAME = "plugin_calendar_collect"
        private const val LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
