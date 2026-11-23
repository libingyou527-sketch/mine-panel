package app.minepanel.collector

import android.Manifest
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.minepanel.core.PermissionUtils
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import app.minepanel.data.repository.PluginEventRepository
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

/**
 * 通话日志插件采集：只记录通话方向、时长与频率；号码经 SHA-256 截断后留存摘要，
 * 不存号码原文、联系人姓名或归属地。未授权或插件关闭时静默跳过。
 */
class CallLogCollectWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val settings = ServiceLocator.settings()
            val enabled = settings.settings.first().plugins[PluginType.CALL_LOG] ?: false
            if (!enabled || !PermissionUtils.hasPermission(applicationContext, Manifest.permission.READ_CALL_LOG)) {
                return Result.success()
            }

            val since = System.currentTimeMillis() - LOOKBACK_MS
            val repo = PluginEventRepository(ServiceLocator.database().pluginEventDao())
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            )
            applicationContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toString()),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(0).orEmpty()
                    val durationSec = cursor.getLong(1)
                    val type = cursor.getInt(2)
                    val date = cursor.getLong(3)
                    val direction = directionLabel(type)
                    val payload = buildString {
                        append("{\"durationSec\":").append(durationSec)
                        append(",\"date\":").append(date)
                        if (number.isNotEmpty()) {
                            append(",\"numberHash\":\"").append(hashNumber(number)).append('"')
                        }
                        append('}')
                    }
                    repo.record(PluginType.CALL_LOG, direction, payload)
                }
            }
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun directionLabel(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        else -> "other"
    }

    private fun hashNumber(number: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(number.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
    }

    companion object {
        const val WORK_NAME = "plugin_calllog_collect"
        private const val LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
