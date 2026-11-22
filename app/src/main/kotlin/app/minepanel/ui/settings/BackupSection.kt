package app.minepanel.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 加密备份区块。
 *
 * 备份包用用户口令经 PBKDF2 + AES-GCM 加密后再写本地文件；换机或重装后选择该文件、
 * 输入同一口令即可恢复业务数据。口令遗忘即不可恢复，也没有云端找回入口。
 * 全程本地完成，不联网、不写剪贴板；包名、联系人、通知正文从不进入备份包。
 */
@Composable
fun BackupSection(
    viewModel: BackupViewModel = viewModel()
) {
    val state by viewModel.state

    val timestamp = remember {
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(
            Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.backupTo(it) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreFrom(it) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "加密备份",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                "备份包由你设置的口令加密，可拷贝到其他存储位置，换机或重装后凭同一口令恢复。" +
                    "口令遗忘即无法找回，应用不保存口令、也没有云端恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.includeRawEvents,
                    onCheckedChange = { viewModel.setIncludeRawEvents(it) }
                )
                Text(
                    "同时纳入原始使用事件（仅粗类别与时长，不含应用包名）",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.lastBackupAt?.let {
                Text(
                    "上次备份：${formatBackupTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    OutlinedButton(
                        onClick = { viewModel.showBackupDialog() },
                        enabled = !state.busy
                    ) { Text("加密备份到本地文件") }
                    OutlinedButton(
                        onClick = { viewModel.showRestoreDialog() },
                        enabled = !state.busy
                    ) { Text("从备份恢复") }
                }
            }

            Text(
                "恢复会校验口令与文件完整性，重复条目自动跳过、不覆盖已有正式记忆；" +
                    "选择文件由你手动确认，应用不联网读取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (state.showDialog) {
        PasswordDialog(
            state = state,
            onDismiss = { viewModel.dismissDialog() },
            onPasswordChange = { viewModel.onPasswordChange(it) },
            onConfirmChange = { viewModel.onConfirmPasswordChange(it) },
            onConfirm = {
                if (viewModel.submitDialog()) {
                    if (state.mode == BackupDialogMode.BACKUP) {
                        backupLauncher.launch("minepanel-backup-$timestamp.mpbak")
                    } else {
                        restoreLauncher.launch(arrayOf("*/*"))
                    }
                }
            }
        )
    }
}

@Composable
private fun PasswordDialog(
    state: BackupUiState,
    onDismiss: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onConfirm: () -> Unit
) {
    val isBackup = state.mode == BackupDialogMode.BACKUP
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBackup) "设置备份口令" else "输入恢复口令") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(if (isBackup) "口令" else "恢复口令") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isBackup) {
                    OutlinedTextField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmChange,
                        label = { Text("确认口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                state.passwordError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    if (isBackup) "请牢记该口令；备份包不含口令，遗忘后无法解密。"
                    else "输入备份时设置的口令，口令错误或文件损坏将无法恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(if (isBackup) "继续选择文件" else "继续选择备份") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatBackupTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
