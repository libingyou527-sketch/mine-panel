package app.minepanel.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.data.export.ImportConflictStrategy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本地明文导出区块。
 *
 * 导出总开关默认关闭；开启后经 SAF 由用户选择文件，写出本地 JSON。
 * 文件为明文，界面显著提示其敏感性；全程不联网、不写剪贴板。
 */
@Composable
fun ExportSection(
    viewModel: ExportViewModel = viewModel()
) {
    val state by viewModel.state

    val timestamp = remember {
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(
            Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.export(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFrom(it) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "本地导出",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = state.allowExport,
                    onCheckedChange = { viewModel.setAllowExport(it) }
                )
            }

            if (!state.allowExport) {
                Text(
                    "导出默认关闭。开启后可把已确认记忆、标签、分值与徽章保存为本地 JSON 文件；" +
                        "文件为明文，请自行妥善保管。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Surface
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = state.includeRawEvents,
                    onCheckedChange = { viewModel.setIncludeRawEvents(it) }
                )
                Text(
                    "同时纳入原始使用事件（仅粗类别与时长，不含应用包名）",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state.lastExportAt?.let {
                Text(
                    "上次导出：${formatExportTime(it)}",
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

            val strategies = remember {
                listOf(
                    ImportConflictStrategy.SKIP_DUPLICATES to "跳过重复",
                    ImportConflictStrategy.OVERWRITE to "覆盖已有",
                    ImportConflictStrategy.APPEND_ALL to "全部追加"
                )
            }
            Text(
                "重复条目处理方式",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                strategies.forEach { (strategy, label) ->
                    FilterChip(
                        selected = state.strategy == strategy,
                        onClick = { viewModel.setStrategy(strategy) },
                        label = { Text(label) }
                    )
                }
            }

            state.lastImportMessage?.let {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "上次导入：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.lastImportIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.lastImportAt?.let { at ->
                        Text(
                            formatExportTime(at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.exporting || state.importing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    OutlinedButton(
                        onClick = { launcher.launch("minepanel-export-$timestamp.json") },
                        enabled = !state.exporting
                    ) { Text("导出到本地文件") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !state.importing
                    ) { Text("从本地文件导入") }
                }
            }

            Text(
                "导入会校验文件格式；默认「跳过重复」不覆盖已有正式记忆，「覆盖已有」会按指纹更新分值/徽章，「全部追加」不去重可能产生重复条目。选择文件由你手动确认，应用不联网读取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "导出文件不包含包名、联系人、通知正文或聊天内容；选择文件由你手动确认，应用不联网上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatExportTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
