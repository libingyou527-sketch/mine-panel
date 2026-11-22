package app.minepanel.ui.attribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.minepanel.rules.DimensionPair

/** 属性依据页：展示分值、本周/上周对比、置信度、样本天数与人话依据；不下诊断结论。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributeDetailSheet(
    pair: DimensionPair,
    lastWeekHasData: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = pair.key.label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            val current = pair.current
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBlock(
                    label = "本周",
                    score = current?.score,
                    confidence = current?.confidence
                )
                val last = if (lastWeekHasData) pair.last else null
                ScoreBlock(label = "上周", score = last?.score, confidence = last?.confidence)
                DeltaBlock(delta = pair.delta, lastWeekHasData = lastWeekHasData)
            }

            val sampleDays = current?.sampleDays ?: 0
            Text(
                text = "样本天数：$sampleDays 天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

            Text(
                text = "依据摘要",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val evidence = current?.evidence.orEmpty()
            if (evidence.isEmpty()) {
                Text(
                    text = "暂无可展示的依据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                evidence.forEach { item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "·",
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = "以上为本地规则基于粗类别元数据的归纳，不构成健康或心理判断。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScoreBlock(label: String, score: Int?, confidence: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = score?.toString() ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "置信度 ${confidence?.let { "${(it * 100).toInt()}%" } ?: "—"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeltaBlock(delta: Int?, lastWeekHasData: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("变化", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val text = when {
            !lastWeekHasData -> "—"
            delta == null -> "—"
            delta > 0 -> "+$delta"
            delta < 0 -> "$delta"
            else -> "持平"
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
