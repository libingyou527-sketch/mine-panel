package app.minepanel.ui.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.plugin.PluginCatalog
import app.minepanel.core.plugin.PluginDescriptor
import app.minepanel.core.plugin.PluginRiskLevel
import app.minepanel.core.model.PluginType

/**
 * 可选插件设置区块（阶段 5 占位）。
 * 开关仅写入本地偏好；未接入采集的插件打开后仍不申请权限、不采集数据、不写插件事件表。
 */
@Composable
fun PluginSection(
    viewModel: PluginSettingsViewModel = viewModel()
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "可选插件",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            PluginCatalog.all.forEach { descriptor ->
                PluginRow(
                    descriptor = descriptor,
                    enabled = plugins[descriptor.type] ?: false,
                    onToggle = { viewModel.toggle(descriptor.type) }
                )
            }

            Text(
                "当前为占位开关：开启后暂不采集对应数据、也不申请相关权限；真实采集将在后续逐项接入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PluginRow(
    descriptor: PluginDescriptor,
    enabled: Boolean,
    onToggle: (PluginType) -> Unit
) {
    val riskColor = when (descriptor.riskLevel) {
        PluginRiskLevel.HIGH -> MaterialTheme.colorScheme.error
        PluginRiskLevel.MEDIUM -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(descriptor.title, style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = { onToggle(descriptor.type) })
        }
        Text(descriptor.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = descriptor.riskLabel,
            style = MaterialTheme.typography.bodySmall,
            color = riskColor
        )
    }
}
