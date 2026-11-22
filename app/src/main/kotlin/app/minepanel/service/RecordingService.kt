package app.minepanel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.minepanel.R
import app.minepanel.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 常驻可见的记录服务：通知明确显示“正在记录”或“记录已暂停”，不做隐蔽后台。
 * 阶段 1 仅承载可见状态与暂停/恢复；实际采集在阶段 2 接入。
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> applyPaused(true)
            ACTION_RESUME -> applyPaused(false)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForegroundWithCurrentState()
        }
        return START_STICKY
    }

    private fun startForegroundWithCurrentState() {
        val paused = runCatching {
            runBlocking {
                ServiceLocator.settings().settings.firstOrNull()?.recordingPaused ?: false
            }
        }.getOrDefault(false)
        publishForeground(paused)
    }

    private fun applyPaused(paused: Boolean) {
        scope.launch {
            ServiceLocator.settings().setRecordingPaused(paused)
            publishForeground(paused)
        }
    }

    private fun publishForeground(paused: Boolean) {
        val notification = buildNotification(paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val title = if (paused) getString(R.string.sentinel_paused) else getString(R.string.sentinel_recording)
        val text = if (paused) {
            "记录已暂停，可随时在应用内恢复"
        } else {
            "仅在本地记录本人使用元数据，不联网、不上传"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "记录状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示正在记录/已暂停的常驻状态"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.minepanel.action.START"
        const val ACTION_PAUSE = "app.minepanel.action.PAUSE"
        const val ACTION_RESUME = "app.minepanel.action.RESUME"
        const val ACTION_STOP = "app.minepanel.action.STOP"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
