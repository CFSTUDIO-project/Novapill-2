package app.novapill.android.overlay.ui

import android.graphics.Color as AndroidColor
import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.NovaGraph
import app.novapill.android.media.MediaUi
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar

/** Monotonic "now" that refreshes every [intervalMs] while [active]. Reading it recomposes the reader. */
@Composable
internal fun rememberTicker(active: Boolean, intervalMs: Long): State<Long> {
    val state = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active, intervalMs) {
        state.longValue = SystemClock.elapsedRealtime()
        if (active) {
            while (isActive) {
                delay(intervalMs)
                state.longValue = SystemClock.elapsedRealtime()
            }
        }
    }
    return state
}

internal class ClockReading(val hour24: Int, val minute: Int)

private fun readClock(): ClockReading {
    val c = Calendar.getInstance()
    return ClockReading(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** Wall-clock hour and minute, refreshed right after every minute boundary. */
@Composable
internal fun rememberClockReading(): ClockReading {
    var reading by remember { mutableStateOf(readClock()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            reading = readClock()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 40L)
        }
    }
    return reading
}

/** Last non-null value, so an exit animation can keep drawing what just went away. */
@Composable
internal fun <T : Any> rememberLatest(value: T?): T? {
    val holder = remember { arrayOfNulls<Any>(1) }
    if (value != null) holder[0] = value
    @Suppress("UNCHECKED_CAST")
    return (value ?: holder[0]) as T?
}

/**
 * Accent colour for the current track. Album art can be very dark or grey, so the colour is lifted to stay
 * readable on pure black; grey art keeps a white accent instead of inventing a hue.
 */
internal fun readableAccent(argb: Int?, intensityPercent: Int): Color {
    val base: Color = if (argb == null) {
        NovaColors.Crimson
    } else {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        if (hsv[1] < 0.12f) {
            Color.White
        } else {
            hsv[1] = hsv[1].coerceIn(0.45f, 0.9f)
            hsv[2] = hsv[2].coerceIn(0.78f, 1f)
            Color(AndroidColor.HSVToColor(hsv))
        }
    }
    return lerp(Color.White, base, (intensityPercent / 100f).coerceIn(0f, 1f))
}

@Composable
internal fun PillText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = if (color != Color.Unspecified) color else style.color,
            textAlign = align ?: style.textAlign,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Launcher icon of an app (cached by the graph); a neutral tile when the app has no icon. */
@Composable
internal fun AppIcon(packageName: String, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(packageName) { NovaGraph.appInfo.iconBitmap(packageName, 128) }
    val shape = RoundedCornerShape(corner)
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.size(size).clip(shape).background(NovaColors.Surface3), contentAlignment = Alignment.Center) {
            NovaIconView(NovaIcon.BELL, NovaColors.Text2, size = size * 0.55f)
        }
    }
}

/** Album artwork with a soft cross-fade on track change; a neutral note tile when there is none. */
@Composable
internal fun Artwork(media: MediaUi?, size: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape).background(NovaColors.Surface3)) {
        Crossfade(targetState = media?.artwork, animationSpec = tween(260), label = "artwork") { bitmap ->
            if (bitmap != null) {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    NovaIconView(NovaIcon.MUSIC, NovaColors.Text3, size = size * 0.46f)
                }
            }
        }
    }
}

@Composable
internal fun CardDots(count: Int, index: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val on = i == index
            val w by animateDpAsState(if (on) 14.dp else 5.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dot")
            Box(
                Modifier
                    .height(5.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(if (on) accent else Color.White.copy(alpha = 0.25f)),
            )
        }
    }
}

/** Capsule button with optional leading icon. Height 40 dp by default; 48 dp where it is a primary control. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: NovaIcon? = null,
    container: Color = NovaColors.Surface3,
    content: Color = NovaColors.Text1,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    fontSize: Int = 13,
) {
    Row(
        modifier
            .pressable(enabled = enabled, label = text, onClick = onClick)
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.45f))
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NovaIconView(icon, if (enabled) content else content.copy(alpha = 0.5f), size = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        PillText(
            text,
            NovaType.Button.copy(fontSize = fontSize.sp),
            color = if (enabled) content else content.copy(alpha = 0.5f),
        )
    }
}

/** Round icon button; [size] is the whole touch target. */
@Composable
internal fun RoundButton(
    icon: NovaIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = Color.Transparent,
    tint: Color = NovaColors.Text1,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .pressable(enabled = enabled, label = label, onClick = onClick)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        NovaIconView(icon, if (enabled) tint else tint.copy(alpha = 0.35f), size = iconSize)
    }
}

/**
 * Horizontal slider for the pill (seek bar and volume). The track thickens while dragged; there is no
 * resting thumb. While the finger is down the local value is shown, then the real value takes over again.
 */
@Composable
internal fun PillSlider(
    fraction: Float,
    onChange: (Float) -> Unit,
    onFinished: (Float) -> Unit,
    enabled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {},
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) local else fraction.coerceIn(0f, 1f)
    val thick by animateDpAsState(if (dragging) 8.dp else 4.dp, spring(dampingRatio = 0.7f, stiffness = 600f), label = "thick")
    val thumb by animateDpAsState(if (dragging) 7.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "thumb")
    Box(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { o -> onFinished((o.x / size.width).coerceIn(0f, 1f)) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        onChange(local)
                    },
                    onDragEnd = {
                        dragging = false
                        onFinished(local)
                    },
                    onDragCancel = {
                        dragging = false
                        onCancel()
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        local = (local + amount / size.width).coerceIn(0f, 1f)
                        onChange(local)
                    },
                )
            }
            .drawBehind {
                val cy = size.height / 2f
                val h = thick.toPx()
                val radius = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(Color.White.copy(alpha = if (enabled) 0.2f else 0.1f), Offset(0f, cy - h / 2f), Size(size.width, h), radius)
                val w = size.width * shown
                if (w > 0.5f) {
                    drawRoundRect(if (enabled) accent else accent.copy(alpha = 0.45f), Offset(0f, cy - h / 2f), Size(w, h), radius)
                }
                val tr = thumb.toPx()
                if (tr > 0.5f) drawCircle(Color.White, tr, Offset(w.coerceIn(tr, size.width - tr), cy))
            },
    )
}

/** Soft radial glow in the accent colour, drawn behind the artwork of the expanded media card. */
internal fun Modifier.accentGlow(accent: Color, strength: Float): Modifier = drawBehind {
    if (strength <= 0.01f) return@drawBehind
    val r = size.maxDimension * 1.15f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.34f * strength), Color.Transparent),
            center = center,
            radius = r,
        ),
        radius = r,
        center = center,
    )
}
