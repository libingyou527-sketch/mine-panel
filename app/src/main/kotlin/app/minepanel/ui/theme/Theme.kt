package app.minepanel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyanAccent = Color(0xFF7FE7D9)
private val BgDeep = Color(0xFF0B0F0E)
private val SurfaceDeep = Color(0xFF141B19)
private val TextPrimary = Color(0xFFE6F2EF)
private val TextMuted = Color(0xFF7E8C88)

private val MinePanelColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF06201C),
    background = BgDeep,
    onBackground = TextPrimary,
    surface = SurfaceDeep,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDeep,
    onSurfaceVariant = TextMuted
)

/** 极简科幻：近黑底 + 青色描边高亮。 */
@Composable
fun MinePanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MinePanelColorScheme,
        shapes = Shapes(),
        content = content
    )
}
