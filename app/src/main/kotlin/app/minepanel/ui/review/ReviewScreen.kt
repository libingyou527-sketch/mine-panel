package app.minepanel.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minepanel.core.model.AttributeKey
import app.minepanel.core.model.EventCategory
import app.minepanel.core.model.label
import app.minepanel.core.time.ReviewPeriod
import app.minepanel.data.repository.ReviewSummary
import app.minepanel.ui.panel.SectionCard

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    viewModel: ReviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text(
                    text = "长周期回顾",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (state.generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Button(onClick = { viewModel.generate() }) { Text("生成回望草稿") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReviewPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.stepBack() }) { Text("← 上一窗") }
                Text(
                    text = state.summary?.window?.label ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { viewModel.stepForward() },
                    enabled = state.stepsBack > 0
                ) { Text("下一窗 →") }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (state.loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                return@Column
            }

            val summary = state.summary
            if (summary == null) {
                Text(
                    text = "暂无可回顾的数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item { OverviewCard(summary) }
                item { state.previous?.let { TrendCard(summary, it) } }
                item { ScoresCard(summary) }
                item { MemoriesCard(summary) }
            }
        }
    }
}

@Composable
private fun OverviewCard(summary: ReviewSummary) {
    SectionCard(title = "使用概览") {
        Text(
            text = "记录 ${summary.sampleDays} 天 · ${formatMinutes(summary.totalScreenTimeMin)}",
            style = MaterialTheme.typography.bodyLarge
        )
        val categories = summary.categoryMinutes.entries.sortedByDescending { it.value }
        if (categories.isEmpty()) {
            Text(
                text = "本窗暂无聚合使用记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            categories.forEach { (category, minutes) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(EventCategory.fromName(category).label, style = MaterialTheme.typography.bodyMedium)
                    Text("${minutes} 分钟", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun TrendCard(current: ReviewSummary, previous: ReviewSummary) {
    SectionCard(title = "与上一窗对比") {
        TrendRow("屏幕时长", current.totalScreenTimeMin - previous.totalScreenTimeMin, "分钟")
        TrendRow("已确认记忆", current.memoryCount - previous.memoryCount, "条")
        TrendRow("主动片段", current.fragmentCount - previous.fragmentCount, "条")
        Text(
            "分值为最新快照，不按窗对比；以上仅为本窗相对上一窗的数量变化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrendRow(label: String, delta: Int, unit: String) {
    val deltaText = when {
        delta > 0 -> "↑ +$delta $unit"
        delta < 0 -> "↓ ${-delta} $unit"
        else -> "持平"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(deltaText, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScoresCard(summary: ReviewSummary) {
    SectionCard(title = "六维参考分值") {
        AttributeKey.entries.forEach { key ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(key.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    summary.scores[key]?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MemoriesCard(summary: ReviewSummary) {
    SectionCard(title = "本窗记忆与片段") {
        Text(
            text = "已确认记忆 ${summary.memoryCount} 条",
            style = MaterialTheme.typography.bodyMedium
        )
        if (summary.memoryHighlights.isEmpty()) {
            Text(
                "暂无已确认记忆。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            summary.memoryHighlights.forEach { highlight ->
                Text("· $highlight", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.size(8.dp))
        Text(
            text = "主动片段 ${summary.fragmentCount} 条",
            style = MaterialTheme.typography.bodyMedium
        )
        if (summary.fragmentHighlights.isEmpty()) {
            Text(
                "暂无主动片段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            summary.fragmentHighlights.forEach { highlight ->
                Text("· $highlight", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun formatMinutes(totalMin: Int): String {
    if (totalMin <= 0) return "几乎没有屏幕使用"
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "屏幕约 $hours 小时 $mins 分" else "屏幕约 $mins 分钟"
}
