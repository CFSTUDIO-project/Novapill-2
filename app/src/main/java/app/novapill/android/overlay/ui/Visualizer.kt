package app.novapill.android.overlay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

private val SPEEDS = floatArrayOf(5.1f, 7.3f, 6.2f, 8.4f)
private val PHASES = floatArrayOf(0f, 1.7f, 3.1f, 4.6f)

/**
 * Decorative equaliser bars. They follow the play/pause state of the player, not the audio signal
 * (Android gives an overlay no access to what another app plays), and the app says so in Settings.
 * Redraws at about 20 fps only while playing and only when motion is allowed.
 */
@Composable
internal fun Visualizer(
    playing: Boolean,
    color: Color,
    intensityPercent: Int,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val animate = playing && !reduced
    val tick = rememberTicker(animate, 50L)
    val lift by animateFloatAsState(if (playing) 1f else 0f, tween(280), label = "vizLift")
    val level = intensityPercent.coerceIn(10, 100) / 100f
    Canvas(modifier) {
        val bars = SPEEDS.size
        val barW = 3.dp.toPx()
        val gap = 2.5.dp.toPx()
        val total = bars * barW + (bars - 1) * gap
        val startX = (size.width - total) / 2f
        val t = if (animate) tick.value / 1000f else 0f
        for (i in 0 until bars) {
            val wave = if (animate) 0.5f + 0.5f * sin(t * SPEEDS[i] + PHASES[i]) else 0.5f + 0.5f * sin(PHASES[i])
            val f = 0.2f + wave * (0.35f + 0.5f * level) * lift
            val h = size.height * f.coerceIn(0.16f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barW + gap), (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
