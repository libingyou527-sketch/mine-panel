package app.minepanel.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.plugin.GeofencePlace
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
    val places by viewModel.places.collectAsStateWithLifecycle()
    var showPlaces by remember { mutableStateOf(false) }

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
                    implemented = descriptor.implemented,
                    onToggle = { viewModel.toggle(descriptor.type) },
                    onManagePlaces = if (descriptor.type == PluginType.GEOFENCE) {
                        { showPlaces = true }
                    } else null
                )
            }

            Text(
                "已接入插件在授予对应系统权限后按开关采集；未接入项（健康连接）开启也不会采集，待后续版本支持。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showPlaces) {
        PlacesDialog(
            places = places,
            onAdd = { label, lat, lng, radius -> viewModel.addPlace(label, lat, lng, radius) },
            onRemove = { label -> viewModel.removePlace(label) },
            onDismiss = { showPlaces = false }
        )
    }
}

@Composable
private fun PluginRow(
    descriptor: PluginDescriptor,
    enabled: Boolean,
    implemented: Boolean,
    onToggle: (PluginType) -> Unit,
    onManagePlaces: (() -> Unit)? = null
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                onManagePlaces?.let {
                    TextButton(onClick = it, enabled = implemented) { Text("常去点") }
                }
                Switch(
                    checked = enabled && implemented,
                    onCheckedChange = { onToggle(descriptor.type) },
                    enabled = implemented
                )
            }
        }
        Text(descriptor.description, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (implemented) descriptor.riskLabel else "${descriptor.riskLabel} · 暂未接入",
            style = MaterialTheme.typography.bodySmall,
            color = riskColor
        )
    }
}

@Composable
private fun PlacesDialog(
    places: List<GeofencePlace>,
    onAdd: (label: String, lat: Double?, lng: Double?, radiusM: Int?) -> Unit,
    onRemove: (label: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常去点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "只记录到达/离开这些地点，不采集连续轨迹。经纬度可从地图 App 分享坐标获取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                places.forEach { place ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${place.label}（半径 ${place.radiusM} 米）",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onRemove(place.label) }) { Text("删除") }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名称，如：家") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("纬度") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("经度") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it },
                    label = { Text("半径（米，可选 50–2000）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    label,
                    latText.toDoubleOrNull(),
                    lngText.toDoubleOrNull(),
                    radiusText.toIntOrNull()
                )
                label = ""
                latText = ""
                lngText = ""
                radiusText = ""
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
