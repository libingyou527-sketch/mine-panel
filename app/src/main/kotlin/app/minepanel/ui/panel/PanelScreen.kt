package app.minepanel.ui.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.PermissionUtils
import app.minepanel.core.model.EventCategory
import app.minepanel.core.model.label
import app.minepanel.rules.DimensionPair
import app.minepanel.ui.attribute.AttributeDetailSheet
import app.minepanel.ui.settings.SettingsViewModel

@Composable
fun PanelScreen(
    onWiped: () -> Unit,
    panelViewModel: PanelViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by panelViewModel.state.collectAsStateWithLifecycle()
    val paused by panelViewModel.paused.collectAsStateWithLifecycle()

    var usageAccess by remember { mutableStateOf(PermissionUtils.hasUsageAccess(context)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var wiping by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showBook by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }

    if (showChat) {
        app.minepanel.ui.chat.ChatScreen(onBack = { showChat = false })
        return
    }

    if (showReview) {
        app.minepanel.ui.review.ReviewScreen(onBack = { showReview = false })
        return
    }

    if (showBook) {
        app.minepanel.ui.memory.MemoryBookScreen(onBack = { showBook = false })
        return
    }

    if (showQueue) {
        app.minepanel.ui.memory.DraftQueueScreen(onBack = { showQueue = false })
        return
    }

    LaunchedEffect(Unit) {
        usageAccess = PermissionUtils.hasUsageAccess(context)
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PanelTopBar(
            paused = paused,
            onTogglePause = panelViewModel::togglePause,
            onOpenQueue = { showQueue = true },
            onOpenReview = { showReview = true },
            onOpenBook = { showBook = true },
            onOpenChat = { showChat = true },
            onDelete = { showDeleteConfirm = true }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        if (!usageAccess) {
            item {
                PermissionBanner(
                    onRequest = {
                        context.startActivity(PermissionUtils.usageAccessSettingsIntent())
                    }
                )
            }
        }

        if (state.isBaseline) {
            item {
                BaselineBanner()
            }
        }

        item {
            LevelCard(
                level = state.level,
                averageScore = state.averageScore,
                progress = state.levelProgress
            )
        }

        item {
            SectionCard(title = "六维画像 · 本周 vs 上周") {
                RadarChart(
                    pairs = state.pairs,
                    lastWeekHasData = state.lastWeekHasData,
                    isBaseline = state.isBaseline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                LegendRow(hasLastWeek = state.lastWeekHasData)
            }
        }

        item {
            SectionCard(title = "属性明细") {
                state.pairs.forEach { pair ->
                    DimensionRow(
                        pair = pair,
                        lastWeekHasData = state.lastWeekHasData,
                        onClick = { panelViewModel.select(pair.key) }
                    )
                }
            }
        }

        item {
            SectionCard(title = "近 14 天使用分布") {
                if (state.timeline.isEmpty()) {
                    EmptyHint("暂无聚合数据，记录开始后在此展示每日粗类别分布。")
                } else {
                    TimelineChart(
                        days = state.timeline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    TimelineLegend(state.timeline)
                }
            }
        }

        item {
            SectionCard(title = "徽章") {
                if (state.badges.isEmpty()) {
                    EmptyHint("持续记录并积累足够样本后，将在此解锁本地徽章。")
                } else {
                    state.badges.forEach { badge ->
                        Text(badge.badgeKey, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        item {
            Text(
                text = "所有数值均由本地规则基于粗类别元数据计算；面板不显示应用包名、联系人或通知内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }

    val selectedPair = state.pairs.firstOrNull { it.key == state.selectedKey }
    if (selectedPair != null) {
        AttributeDetailSheet(
            pair = selectedPair,
            lastWeekHasData = state.lastWeekHasData,
            onDismiss = { panelViewModel.select(null) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!wiping) showDeleteConfirm = false },
            title = { Text("删除全部数据？") },
            text = {
                Text(
                    "将物理删除本地加密数据库、设置与密钥包装，此操作不可恢复。" +
                        "全程在设备本地完成，不联网、不上传。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    wiping = true
                    settingsViewModel.wipeAll(onDone = onWiped)
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }

    if (state.showFallbackNotice) {
        AlertDialog(
            onDismissRequest = { panelViewModel.dismissFallbackNotice() },
            title = { Text("本次为兜底回顾") },
            text = {
                Text(
                    "本地模型暂不可用（超时或内存不足），本次回顾由内置兜底模板生成，" +
                        "内容尚未自动入库。请到草案队列人工确认。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    panelViewModel.dismissFallbackNotice()
                    showQueue = true
                }) { Text("前往草案队列") }
            },
            dismissButton = {
                TextButton(onClick = { panelViewModel.dismissFallbackNotice() }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun PanelTopBar(
    paused: Boolean,
    onTogglePause: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenBook: () -> Unit,
    onOpenChat: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "mine panel",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (paused) "记录已暂停" else "正在记录 · 仅本地",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TopBarButton(onClick = onOpenChat, label = "聊天", modifier = Modifier)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopBarButton(onClick = onOpenQueue, label = "草案", modifier = Modifier.weight(1f).fillMaxWidth())
            TopBarButton(onClick = onOpenReview, label = "回顾", modifier = Modifier.weight(1f).fillMaxWidth())
            TopBarButton(onClick = onOpenBook, label = "记忆本", modifier = Modifier.weight(1f).fillMaxWidth())
            TopBarButton(onClick = onTogglePause, label = if (paused) "恢复记录" else "暂停", modifier = Modifier.weight(1f).fillMaxWidth())
            TopBarButton(onClick = onDelete, label = "删除", modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun TopBarButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "需要“使用情况访问”权限以读取本人设备使用元数据。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRequest) { Text("去授权") }
        }
    }
}

@Composable
private fun BaselineBanner() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = "基线建立中：前 7 天只积累本地元数据，面板分值为初步参考，暂不下结论。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun LevelCard(level: Int, averageScore: Int, progress: Float) {
    SectionCard(title = "当前等级") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Lv.$level",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    text = "六维均分 $averageScore",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LegendRow(hasLastWeek: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LegendDot(MaterialTheme.colorScheme.primary, "本周")
        if (hasLastWeek) {
            LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), "上周")
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, modifier = Modifier.size(10.dp), shape = MaterialTheme.shapes.small) {}
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DimensionRow(
    pair: DimensionPair,
    lastWeekHasData: Boolean,
    onClick: () -> Unit
) {
    val current = pair.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(pair.key.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "置信度 ${current?.let { "${(it.confidence * 100).toInt()}%" } ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current?.score?.toString() ?: "—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = deltaText(pair.delta, lastWeekHasData),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun deltaText(delta: Int?, hasLastWeek: Boolean): String = when {
    !hasLastWeek || delta == null -> ""
    delta > 0 -> "+$delta"
    delta < 0 -> "$delta"
    else -> "持平"
}

@Composable
private fun TimelineLegend(days: List<app.minepanel.rules.PanelTimelineDay>) {
    val present = EventCategory.entries.filter { category ->
        days.any { day -> (day.categoryMs[category.name] ?: 0L) > 0 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        present.forEach { category ->
            LegendDot(
                color = categoryColor(category),
                label = category.label
            )
        }
    }
}

@Composable
private fun categoryColor(category: EventCategory): androidx.compose.ui.graphics.Color {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (category) {
        EventCategory.WORK -> primary
        EventCategory.SOCIAL -> primary.copy(alpha = 0.78f)
        EventCategory.MEDIA -> primary.copy(alpha = 0.55f)
        EventCategory.TOOL -> muted.copy(alpha = 0.8f)
        EventCategory.REST -> primary.copy(alpha = 0.35f)
        EventCategory.UNMAPPED -> muted.copy(alpha = 0.45f)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
