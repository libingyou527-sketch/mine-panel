package app.minepanel.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.minepanel.core.model.EventCategory
import app.minepanel.rules.PanelTimelineDay

/** 近 14 天粗类别堆叠柱；只画类别时长，不出现包名。 */
@Composable
fun TimelineChart(
    days: List<PanelTimelineDay>,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val categoryColors = rememberCategoryColors(primary, muted)

    Canvas(modifier = modifier) {
        if (days.isEmpty()) return@Canvas
        val maxTotal = days.maxOfOrNull { it.totalMs } ?: 0L
        if (maxTotal <= 0) return@Canvas

        val slot = size.width / days.size
        val barWidth = slot * 0.55f
        val categoryOrder = EventCategory.entries.toList()

        days.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2f
            var yCursor = size.height
            categoryOrder.forEach { category ->
                val ms = day.categoryMs[category.name] ?: return@forEach
                if (ms <= 0) return@forEach
                val segmentHeight = size.height * (ms.toFloat() / maxTotal)
                yCursor -= segmentHeight
                drawRect(
                    color = categoryColors.getValue(category),
                    topLeft = Offset(x, yCursor),
                    size = Size(barWidth, segmentHeight)
                )
            }
        }
    }
}

@Composable
private fun rememberCategoryColors(primary: Color, muted: Color): Map<EventCategory, Color> =
    mapOf(
        EventCategory.WORK to primary,
        EventCategory.SOCIAL to primary.copy(alpha = 0.78f),
        EventCategory.MEDIA to primary.copy(alpha = 0.55f),
        EventCategory.TOOL to muted.copy(alpha = 0.8f),
        EventCategory.REST to primary.copy(alpha = 0.35f),
        EventCategory.UNMAPPED to muted.copy(alpha = 0.45f)
    )
