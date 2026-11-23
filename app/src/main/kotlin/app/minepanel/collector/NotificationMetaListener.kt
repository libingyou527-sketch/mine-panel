package app.minepanel.collector

import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService
import app.minepanel.core.ServiceLocator
import app.minepanel.core.model.PluginType
import app.minepanel.data.repository.PluginEventRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 通知元数据插件监听服务：仅记录包名、通知频道与发帖时间戳。
 * 不读取通知标题、正文、图标或任何 extras 内容；插件关闭时直接忽略，不写库。
 */
class NotificationMetaListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        runCatching {
            val enabled = runBlocking {
                ServiceLocator.settings().settings.first().plugins[PluginType.NOTIFICATION_META] ?: false
            }
            if (!enabled) return@runCatching

            val packageName = notification.packageName ?: return@runCatching
            val channelId = notification.notification?.channelId
            val payload = buildString {
                append("{\"channel\":")
                append(PluginJson.escape(channelId.orEmpty()))
                append(",\"postedAt\":")
                append(notification.postTime)
                append('}')
            }
            runBlocking {
                PluginEventRepository(ServiceLocator.database().pluginEventDao())
                    .record(PluginType.NOTIFICATION_META, packageName, payload)
            }
        }
    }
}

/** 插件脱敏 JSON 的最小字符串转义；仅用于频道名等短元数据。 */
internal object PluginJson {
    fun escape(value: String): String {
        val sb = StringBuilder("\"")
        value.forEach { ch ->
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
