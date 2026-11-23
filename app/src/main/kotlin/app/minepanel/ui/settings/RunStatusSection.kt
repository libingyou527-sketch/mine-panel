package app.minepanel.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RunStatusSection(
    viewModel: RunStatusViewModel = viewModel()
) {
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
                .padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "最近一次推理",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (state.hasRecord) {
                Text(
                    text = statusLabel(state.status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.status == "SUCCESS") MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                val meta = listOf(
                    engineLabel(state.engineName),
                    "耗时 ${formatLatency(state.latencyMs)}",
                    formatTime(state.finishedAt)
                ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "尚未运行过内置模型推理；生成一次后这里会显示结果状态、引擎与耗时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "SUCCESS" -> "模型成功"
    "FALLBACK_TEMPLATE" -> "模板降级"
    "TIMEOUT" -> "超时"
    "ERROR" -> "错误"
    else -> status
}

private fun engineLabel(engine: String): String = when (engine) {
    "gguf" -> "内置模型"
    "template" -> "模板"
    else -> engine.ifBlank { "未知引擎" }
}

private fun formatLatency(latencyMs: Long): String = when {
    latencyMs <= 0 -> "-"
    latencyMs < 1000 -> "${latencyMs} ms"
    else -> "${"%.1f".format(latencyMs / 1000.0)} s"
}

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return "-"
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(formatter)
}
