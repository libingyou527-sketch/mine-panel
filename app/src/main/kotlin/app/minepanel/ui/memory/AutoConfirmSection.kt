package app.minepanel.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 草案自动确认区块。
 *
 * 默认开启：本地模型成功生成的草案自动裁决；仅记忆要点写入正式记忆，
 * 状态标签与回顾文案只标记为已确认；兜底模板、超时或异常生成的内容仍进入待确认队列；
 * 重复内容不重复写入。关闭后新产出重新走人工确认。全程本地，不联网。
 */
@Composable
fun AutoConfirmSection(viewModel: DraftViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "自动确认记忆",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = state.autoConfirmEnabled,
                    onCheckedChange = { viewModel.toggleAutoConfirm(it) }
                )
            }
            Text(
                if (state.autoConfirmEnabled) {
                    "已开启：本地模型成功生成的草案自动裁决，记忆要点写入正式记忆，标签与回顾文案标记为已确认；" +
                        "兜底模板、超时或异常内容仍需手动确认，重复内容不重复写入。"
                } else {
                    "已关闭。开启后本地模型成功生成的草案可自动裁决，免去逐条确认；" +
                        "模型不可用时的兜底文案仍进入待确认队列。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
