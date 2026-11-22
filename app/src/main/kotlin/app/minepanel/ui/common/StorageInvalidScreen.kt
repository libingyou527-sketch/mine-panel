package app.minepanel.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 安全存储失效页：口令包装无法从系统密钥库还原，旧加密库不可恢复。
 * 不自动删除任何数据；用户二次确认后才回调 [onWipe] 物理擦除并重启。
 *
 * [lastBackupEpochMs] 取自独立于加密库的本地设置（上次明文导出时间）；
 * 失效状态下无法抢救打开旧库，页面只回显备份时间与可恢复性说明，不提供导出按钮。
 */
@Composable
fun StorageInvalidScreen(
    onWipe: () -> Unit,
    lastBackupEpochMs: Long? = null
) {
    var showConfirm by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "安全存储已失效",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "无法从系统密钥库还原本地数据库口令，现有加密数据已不可恢复。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = backupNotice(lastBackupEpochMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "确认后将物理删除本地数据库、设置与已导入的模型文件；全程在设备本地完成，" +
                "不联网、不上传，且此操作不可恢复。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { if (!wiping) showConfirm = true },
            enabled = !wiping,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (wiping) "正在清除…" else "清除本地数据并重新开始")
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!wiping) showConfirm = false },
            title = { Text("确认清除全部本地数据？") },
            text = {
                Text("旧加密库已无法打开。清除后将回到首次引导，原有记录与草案不会保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    wiping = true
                    onWipe()
                }) { Text("确认清除") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            }
        )
    }
}

internal fun backupNotice(epochMs: Long?): String =
    if (epochMs != null && epochMs > 0) {
        "上次本地备份：${formatBackupTime(epochMs)}。旧库仍无法直接打开；" +
            "若要保留历史记录，清除后可从该备份文件重新导入。"
    } else {
        "未找到本地备份，清除后原有记录、草案与徽章都不会保留。"
    }

internal fun formatBackupTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
