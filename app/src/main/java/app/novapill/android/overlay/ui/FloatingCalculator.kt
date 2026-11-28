package app.novapill.android.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CalcPad
import app.novapill.android.overlay.FloatingHandle
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType

private val CALC_ROWS = listOf(
    listOf("C", "⌫", "(", ")"),
    listOf("7", "8", "9", "÷"),
    listOf("4", "5", "6", "×"),
    listOf("1", "2", "3", "−"),
    listOf("0", ".", "%", "+"),
)

/** Holds the calculator's keypad state for as long as its window is open. */
@Composable
internal fun FloatingCalculatorWindow(handle: FloatingHandle) {
    var pad by remember { mutableStateOf(CalcPad()) }
    FloatingCalculator(
        pad = pad,
        onKey = { key -> pad = CalcPad.press(pad, key) },
        onClose = { handle.close() },
    )
}

/**
 * The calculator that lives in a floating overlay window. Dragging and resizing are handled by transparent
 * touch areas of the window itself (they need raw screen coordinates), which sit over the title bar and the
 * bottom-right grip drawn here.
 */
@Composable
internal fun FloatingCalculator(pad: CalcPad, onKey: (String) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(NovaColors.Black)
            .border(1.dp, NovaColors.Hairline, shape),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NovaDot()
                PillText("CALCULATOR", NovaType.Label, modifier = Modifier.weight(1f).padding(start = 8.dp))
                RoundButton(
                    icon = NovaIcon.CLOSE,
                    label = "Close calculator",
                    onClick = onClose,
                    size = 36.dp,
                    container = NovaColors.Surface3,
                    iconSize = 16.dp,
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                PillText(
                    pad.expr.ifEmpty { " " },
                    NovaType.Body.copy(fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    color = NovaColors.Text3,
                    align = TextAlign.End,
                )
                PillText(
                    pad.shown,
                    NovaType.NumberBig.copy(fontSize = 38.sp, lineHeight = 44.sp),
                    modifier = Modifier.fillMaxWidth(),
                    color = if (pad.error) NovaColors.Danger else NovaColors.Text1,
                    align = TextAlign.End,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 12.dp, top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (row in CALC_ROWS) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (key in row) CalcKey(key, Modifier.weight(1f).fillMaxHeight(), onKey)
                    }
                }
                CalcKey("=", Modifier.weight(1f).fillMaxWidth(), onKey)
            }
        }
        ResizeGrip(Modifier.align(Alignment.BottomEnd).padding(6.dp))
    }
}

@Composable
private fun NovaDot() {
    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(NovaColors.Crimson))
}

@Composable
private fun CalcKey(label: String, modifier: Modifier, onKey: (String) -> Unit) {
    val isOperator = label == "÷" || label == "×" || label == "−" || label == "+"
    val container: Color = when {
        label == "=" -> NovaColors.Crimson
        isOperator -> NovaColors.Surface3
        label == "C" || label == "⌫" -> NovaColors.Surface2
        else -> NovaColors.Surface1
    }
    val content: Color = when {
        label == "=" -> Color.White
        isOperator -> NovaColors.Crimson
        label == "C" -> NovaColors.Danger
        else -> NovaColors.Text1
    }
    Box(
        modifier
            .pressable(label = keyDescription(label), onClick = { onKey(label) })
            .clip(RoundedCornerShape(14.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        PillText(label, NovaType.Title.copy(fontSize = 21.sp, lineHeight = 26.sp), color = content)
    }
}

private fun keyDescription(label: String): String = when (label) {
    "C" -> "Clear"
    "⌫" -> "Delete last"
    "÷" -> "Divide"
    "×" -> "Multiply"
    "−" -> "Subtract"
    "+" -> "Add"
    "%" -> "Percent"
    "=" -> "Equals"
    "(" -> "Open bracket"
    ")" -> "Close bracket"
    "." -> "Decimal point"
    else -> label
}

/** Two diagonal strokes that show where the corner can be dragged. The touch area itself belongs to the window. */
@Composable
private fun ResizeGrip(modifier: Modifier) {
    Canvas(modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(NovaColors.Text3, Offset(w, h * 0.35f), Offset(w * 0.35f, h), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(NovaColors.Text3, Offset(w, h * 0.7f), Offset(w * 0.7f, h), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
