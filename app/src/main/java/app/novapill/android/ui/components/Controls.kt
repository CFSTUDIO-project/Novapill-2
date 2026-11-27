package app.novapill.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import kotlin.math.roundToInt

@Composable
fun NText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NovaType.Body,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
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

/** Square icon button with a 48 dp touch target. */
@Composable
fun IconAction(icon: NovaIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = NovaColors.Text1, size: Dp = 48.dp) {
    Box(modifier.size(size).pressable(label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        NovaIconView(icon, tint, size = 22.dp)
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(NovaColors.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconAction(NovaIcon.ARROW_BACK, "Back", onBack) else Spacer(Modifier.width(12.dp))
            NText(title, Modifier.weight(1f).padding(start = 4.dp), NovaType.Title, maxLines = 1)
        }
        val body = if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)
        Column(body.navigationBarsPadding().imePadding()) {
            content()
            if (scroll) Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    NText(text.uppercase(), modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 6.dp), NovaType.Label)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(NovaColors.Hairline))
}

/** Rounded dark surface with a hairline edge. */
@Composable
fun NovaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(NovaColors.Surface2)
            .border(1.dp, NovaColors.Hairline, RoundedCornerShape(22.dp)),
        content = content,
    )
}

@Composable
fun NovaSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, label: String? = null) {
    val position by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 700f), label = "switch")
    val track = lerp(Color(0xFF2B2B31), NovaColors.Crimson, position)
    val base = modifier.size(width = 52.dp, height = 32.dp)
    val clickable = if (onChange != null) base.pressable(label = label, pressedScale = 0.96f, onClick = { onChange(!checked) }) else base
    Box(clickable.clip(CircleShape).background(track), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = 4.dp + 20.dp * position).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, label = title, pressedScale = 0.99f, onClick = { onChange(!checked) })
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            NText(title, style = NovaType.BodyStrong, color = if (enabled) Color.Unspecified else NovaColors.Text3)
            if (subtitle != null) NText(subtitle, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        Spacer(Modifier.width(16.dp))
        NovaSwitch(checked, null)
    }
}

@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: NovaIcon? = null,
    trailing: String? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(label = title, pressedScale = 0.99f, onClick = onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NovaIconView(icon, if (danger) NovaColors.Danger else NovaColors.Text2, size = 22.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            NText(title, style = NovaType.BodyStrong, color = if (danger) NovaColors.Danger else Color.Unspecified)
            if (subtitle != null) NText(subtitle, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            NText(trailing, style = NovaType.Caption.copy(fontSize = 14.sp), maxLines = 1)
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            NovaIconView(NovaIcon.CHEVRON_RIGHT, NovaColors.Text4, size = 18.dp)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        NText(label, style = NovaType.Caption)
        NText(value, Modifier.padding(top = 2.dp), if (mono) NovaType.Mono else NovaType.BodyStrong)
    }
}

/** Slider with a resting thumb. [onChange] receives a fraction in 0..1. */
@Composable
fun NovaSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) local else fraction.coerceIn(0f, 1f)
    val thumb by animateDpAsState(if (dragging) 13.dp else 10.dp, spring(dampingRatio = 0.7f, stiffness = 700f), label = "thumb")
    Box(
        modifier
            .height(40.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onTap = { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) })
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        local = (o.x / size.width).coerceIn(0f, 1f)
                        onChange(local)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        local = (local + amount / size.width).coerceIn(0f, 1f)
                        onChange(local)
                    },
                )
            }
            .drawBehind {
                val cy = size.height / 2f
                val h = 4.dp.toPx()
                val radius = CornerRadius(h / 2f, h / 2f)
                val r = thumb.toPx()
                val x = (size.width * shown).coerceIn(r, size.width - r)
                drawRoundRect(Color.White.copy(alpha = if (enabled) 0.16f else 0.08f), Offset(0f, cy - h / 2f), Size(size.width, h), radius)
                drawRoundRect(if (enabled) NovaColors.Crimson else NovaColors.Crimson.copy(alpha = 0.4f), Offset(0f, cy - h / 2f), Size(x, h), radius)
                drawCircle(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), r, Offset(x, cy))
            },
    )
}

@Composable
fun IntSliderRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    step: Int = 1,
    unit: String = "",
    subtitle: String? = null,
    format: ((Int) -> String)? = null,
) {
    val label = format?.invoke(value) ?: "$value$unit"
    val range = (max - min).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NText(title, Modifier.weight(1f), NovaType.BodyStrong)
            NText(label, style = NovaType.NumberMid)
        }
        if (subtitle != null) NText(subtitle, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        NovaSlider(
            fraction = (value - min).toFloat() / range,
            onChange = { f ->
                val raw = min + f * range
                val snapped = (min + ((raw - min) / step).roundToInt() * step).coerceIn(min, max)
                if (snapped != value) onChange(snapped)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SegmentedRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        NText(title, style = NovaType.BodyStrong)
        if (subtitle != null) NText(subtitle, Modifier.padding(top = 2.dp), NovaType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(CircleShape)
                .background(NovaColors.Surface2)
                .border(1.dp, NovaColors.Hairline, CircleShape)
                .padding(3.dp),
        ) {
            options.forEachIndexed { index, text ->
                val on = index == selected
                val bg by animateColorAsState(if (on) Color.White else Color.Transparent, tween(180), label = "seg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(label = text, pressedScale = 0.97f, onClick = { onSelect(index) })
                        .clip(CircleShape)
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    NText(text, style = NovaType.Button.copy(fontSize = 13.sp), color = if (on) Color.Black else NovaColors.Text2, maxLines = 1)
                }
            }
        }
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: NovaIcon? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val bg = when (kind) {
        ButtonKind.PRIMARY -> NovaColors.Crimson
        ButtonKind.SECONDARY -> NovaColors.Surface3
        ButtonKind.DANGER -> Color.Transparent
        ButtonKind.GHOST -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.PRIMARY -> Color.White
        ButtonKind.SECONDARY -> NovaColors.Text1
        ButtonKind.DANGER -> NovaColors.Danger
        ButtonKind.GHOST -> NovaColors.Text2
    }
    val edge = if (kind == ButtonKind.DANGER) Modifier.border(1.dp, NovaColors.Danger.copy(alpha = 0.5f), CircleShape) else Modifier
    Row(
        modifier
            .pressable(enabled = enabled, label = text, onClick = onClick)
            .height(height)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .then(edge)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            NovaIconView(icon, if (enabled) fg else fg.copy(alpha = 0.5f), size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        NText(text, style = NovaType.Button, color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

@Composable
fun NovaTextField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = NovaType.BodyStrong,
        cursorBrush = SolidColor(NovaColors.Crimson),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NovaColors.Surface2)
                    .border(1.dp, NovaColors.Hairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) NText(placeholder, style = NovaType.BodyStrong, color = NovaColors.Text4)
                inner()
            }
        },
    )
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        NText(text.uppercase(), style = NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp), color = color, maxLines = 1)
    }
}

/** Launcher icon of an installed app (cached by the graph). */
@Composable
fun AppIconView(packageName: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(packageName) { NovaGraph.appInfo.iconBitmap(packageName, 128) }
    val shape = RoundedCornerShape(size * 0.26f)
    if (bitmap != null) {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, null, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(shape).background(NovaColors.Surface3), contentAlignment = Alignment.Center) {
            NovaIconView(NovaIcon.APPS, NovaColors.Text3, size = size * 0.5f)
        }
    }
}

@Composable
fun Gap(height: Int) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun GapW(width: Int) {
    Spacer(Modifier.width(width.dp))
}
