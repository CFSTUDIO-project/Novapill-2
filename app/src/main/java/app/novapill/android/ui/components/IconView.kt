package app.novapill.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private class ParsedPart(val path: Path, val filled: Boolean)

/** Draws one of the custom 24 x 24 vector icons. No icon font, no emoji. */
@Composable
fun NovaIconView(icon: NovaIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val parts = remember(icon) {
        icon.parts.map { ParsedPart(PathParser().parsePathString(it.pathData).toPath(), it.filled) }
    }
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            for (part in parts) {
                if (part.filled) {
                    drawPath(part.path, tint)
                    drawPath(part.path, tint, style = Stroke(width = 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawPath(part.path, tint, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/**
 * Click target with a short spring "press" instead of the default indication, so no ripple or debug
 * overlay ever appears on the pill. [label] becomes the accessibility description.
 */
fun Modifier.pressable(
    enabled: Boolean = true,
    label: String? = null,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        label = "press",
    )
    val described = if (label != null) Modifier.semantics { contentDescription = label } else Modifier
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(described)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
}
