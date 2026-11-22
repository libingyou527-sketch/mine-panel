package app.minepanel.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import app.minepanel.rules.DimensionPair
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

/**
 * 六维雷达图：本周青色填充描边，上周灰色轮廓叠加；基线期整体降饱和。
 * 分值全部来自规则引擎，此组件只做渲染。
 */
@Composable
fun RadarChart(
    pairs: List<DimensionPair>,
    lastWeekHasData: Boolean,
    isBaseline: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lastColor = muted.copy(alpha = 0.55f)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val n = pairs.size
        if (n == 0) return@Canvas
        val centerX = size.width / 2.0
        val centerY = size.height / 2.0
        val labelRadiusPx = LABEL_RADIUS.toPx()
        val radius = (minOf(size.width, size.height) / 2.0 - labelRadiusPx)

        fun point(index: Int, value: Double): Offset {
            val angle = (2.0 * PI * index / n) - PI / 2.0
            val r = radius * (value.coerceIn(0.0, 100.0) / 100.0)
            return Offset(
                (centerX + r * cos(angle)).toFloat(),
                (centerY + r * sin(angle)).toFloat()
            )
        }

        // 网格三圈 + 放射轴
        listOf(0.33, 0.66, 1.0).forEach { ring ->
            val grid = Path().apply {
                repeat(n) { i ->
                    val p = point(i, ring * 100.0)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(grid, muted.copy(alpha = 0.16f), style = Stroke(1.dp.toPx()))
        }
        repeat(n) { i ->
            drawLine(
                color = muted.copy(alpha = 0.10f),
                start = Offset(centerX.toFloat(), centerY.toFloat()),
                end = point(i, 100.0),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 上周轮廓（无上周数据时不绘制）
        if (lastWeekHasData) {
            val lastPath = Path().apply {
                repeat(n) { i ->
                    val value = pairs[i].last?.score ?: 0
                    val p = point(i, value.toDouble())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(lastPath, lastColor, style = Stroke(1.5.dp.toPx()))
        }

        // 本周轮廓
        val currentPath = Path().apply {
            repeat(n) { i ->
                val value = pairs[i].current?.score ?: 0
                val p = point(i, value.toDouble())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        val fillAlpha = if (isBaseline) 0.10f else 0.24f
        drawPath(currentPath, primary.copy(alpha = fillAlpha))
        drawPath(currentPath, primary, style = Stroke(2.dp.toPx()))
        repeat(n) { i ->
            val value = pairs[i].current?.score ?: 0
            drawCircle(primary, radius = 3.dp.toPx(), center = point(i, value.toDouble()))
        }

        // 维度标签（顶点外侧）
        pairs.forEachIndexed { i, pair ->
            val angle = (2.0 * PI * i / n) - PI / 2.0
            val labelR = radius + labelRadiusPx * 0.6
            val labelCenter = Offset(
                (centerX + labelR * cos(angle)).toFloat(),
                (centerY + labelR * sin(angle)).toFloat()
            )
            val layout = textMeasurer.measure(pair.key.label)
            drawText(
                layout,
                topLeft = Offset(
                    labelCenter.x - layout.size.width / 2f,
                    labelCenter.y - layout.size.height / 2f
                )
            )
        }
    }
}

private val LABEL_RADIUS = 28.dp
