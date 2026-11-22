package app.minepanel.collector

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.minepanel.core.model.CategoryRules
import app.minepanel.core.model.EventType
import app.minepanel.data.dao.RawUsageEventDao
import app.minepanel.data.db.entity.RawUsageEvent
import app.minepanel.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

data class CollectResult(val blockCount: Int, val rowCount: Int)

/**
 * 使用情况采集器：读取系统 UsageEvents，经配对器得到前台块，
 * 在内存中映射为粗类别后落库；原始包名不写入数据库。
 * 未授予使用情况访问权限时查询结果为空，不抛错、不写空包名。
 */
class UsageCollector(
    private val context: Context,
    private val dao: RawUsageEventDao,
    private val settings: SettingsStore
) {

    suspend fun collect(): CollectResult {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val snapshot = settings.settings.first()
        val now = System.currentTimeMillis()
        val from = snapshot.lastSyncEpochMs ?: (now - DEFAULT_LOOKBACK_MS)

        val events = manager.queryEvents(from, now)
        val matcher = ForegroundBlockMatcher()
        val event = UsageEvents.Event()
        var lastTimestamp = from

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            lastTimestamp = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                    event.packageName?.let { matcher.resume(it, event.timeStamp) }
                UsageEvents.Event.ACTIVITY_PAUSED ->
                    event.packageName?.let { matcher.pause(it, event.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE ->
                    matcher.screenOff(event.timeStamp)
                // 其余事件类型本阶段不采集
            }
        }

        val blocks = matcher.finish(lastTimestamp)
        val rows = blocks.map { block ->
            RawUsageEvent(
                timestamp = block.startMs,
                eventType = EventType.FOREGROUND.name,
                category = CategoryRules.categorize(block.packageName).name,
                durationMs = block.durationMs,
                sessionId = null
            )
        }
        if (rows.isNotEmpty()) {
            dao.insertAll(rows)
        }
        settings.setLastSyncEpochMs(now)
        return CollectResult(blocks.size, rows.size)
    }

    companion object {
        private const val DEFAULT_LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
