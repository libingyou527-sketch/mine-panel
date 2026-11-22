package app.minepanel.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.PermissionUtils
import app.minepanel.core.model.PluginType
import app.minepanel.core.plugin.PluginCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val agreed by viewModel.agreed.collectAsStateWithLifecycle()
    val usageOk by viewModel.usageAccessOk.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshUsageAccess(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (step) {
            0 -> PrivacyStep(
                agreed = agreed,
                onAgree = viewModel::agree,
                onNext = viewModel::next
            )
            1 -> UsageAccessStep(
                granted = usageOk,
                onOpenSettings = {
                    context.startActivity(PermissionUtils.usageAccessSettingsIntent())
                },
                onRefresh = { viewModel.refreshUsageAccess(context) },
                onNext = viewModel::next
            )
            2 -> PluginStep(
                plugins = viewModel.plugins,
                onToggle = viewModel::togglePlugin,
                onFinish = {
                    scope.launch {
                        viewModel.complete()
                        onFinished()
                    }
                }
            )
        }
    }
}

@Composable
private fun PrivacyStep(
    agreed: Boolean,
    onAgree: () -> Unit,
    onNext: () -> Unit
) {
    Text("仅记录本人", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
        listOf(
            "本应用只记录设备持有者本人的使用元数据，建议在个人专用设备上使用。",
            "不监控他人；共享设备下元数据无法天然区分使用者。",
            "不读取聊天、通知正文、屏幕内容、联系人；不联网、不上传、不含广告与云备份。",
            "面板内容仅为自我观察参考，不构成健康、心理或人格诊断。"
        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agreed, onCheckedChange = { if (it) onAgree() })
        Text("我确认此设备为本人专用，仅记录本人使用情况", style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, enabled = agreed, modifier = Modifier.fillMaxWidth()) {
        Text("继续")
    }
}

@Composable
private fun UsageAccessStep(
    granted: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onNext: () -> Unit
) {
    Text("使用情况访问", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        "需要“使用情况访问”权限以读取应用前后台切换与使用时长等元数据。" +
            "应用只在本地处理这些数据，不会上传。",
        style = MaterialTheme.typography.bodyMedium
    )
    if (granted) {
        Text("已授权", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onOpenSettings) { Text("前往系统设置") }
            TextButton(onClick = onRefresh) { Text("刷新状态") }
        }
    }
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, enabled = granted, modifier = Modifier.fillMaxWidth()) {
        Text("继续")
    }
}

@Composable
private fun PluginStep(
    plugins: Map<PluginType, MutableStateFlow<Boolean>>,
    onToggle: (PluginType) -> Unit,
    onFinish: () -> Unit
) {
    Text("可选插件（默认关闭）", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    Text(
        "以下插件默认全部关闭，可稍后在设置中逐项开启。未开启时不申请对应权限、不采集相关数据。",
        style = MaterialTheme.typography.bodyMedium
    )
    Column(
        modifier = Modifier
            .height(320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PluginCatalog.all.forEach { descriptor ->
            val checked = plugins[descriptor.type]?.collectAsStateWithLifecycle()?.value ?: false
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(descriptor.title, style = MaterialTheme.typography.titleMedium)
                        Switch(checked = checked, onCheckedChange = { onToggle(descriptor.type) })
                    }
                    Text(descriptor.description, style = MaterialTheme.typography.bodyMedium)
                    Text(descriptor.riskLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
        Text("完成")
    }
}
