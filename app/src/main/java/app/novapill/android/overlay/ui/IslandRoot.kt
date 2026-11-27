package app.novapill.android.overlay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.media.MediaUi
import app.novapill.android.notif.PeekPayload
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandLayout
import app.novapill.android.overlay.IslandUi
import app.novapill.android.overlay.LayoutRequest
import app.novapill.android.overlay.PillKind
import app.novapill.android.overlay.ScreenInfo
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Matches IslandCoordinator.EXPANDED_TOP_PADDING_DP: the content of a tall panel starts this far below the top edge. */
private const val EXPANDED_TOP_PADDING_DP = 16f

private data class PanelKey(val kind: PillKind, val card: CardKind?, val lyrics: Boolean, val token: String)

/**
 * Draws the island for one [IslandUi]. The same function renders the real overlay window and the
 * interactive preview on the dashboard, so what the preview shows is what the pill does.
 *
 * Text is sized only by the user's "Font size" setting, not by the system font scale: the pill has a fixed,
 * hand-tuned geometry and would otherwise clip.
 */
@Composable
fun IslandRoot(ui: IslandUi, actions: IslandActions, modifier: Modifier = Modifier) {
    val base = LocalDensity.current
    val density = remember(base.density, ui.settings.fontScalePercent) {
        Density(base.density, ui.settings.fontScalePercent / 100f)
    }
    CompositionLocalProvider(LocalDensity provides density) {
        if (ui.repositioning) {
            RepositionLayer(ui, actions, modifier)
        } else {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Pill(ui, actions, interactive = true)
            }
        }
    }
}

@Composable
private fun Pill(ui: IslandUi, actions: IslandActions, interactive: Boolean) {
    val s = ui.settings
    val g = ui.geometry
    val reduced = ui.reducedMotion

    val shown by animateFloatAsState(
        targetValue = if (ui.visible) 1f else 0f,
        animationSpec = if (reduced) tween(120) else spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "shown",
    )
    if (!ui.visible && shown < 0.01f) return

    val spec: AnimationSpec<Dp> = if (reduced) tween(140) else spring(dampingRatio = 0.76f, stiffness = 340f)
    val width by animateDpAsState(g.size.widthDp.dp, spec, label = "pillWidth")
    val height by animateDpAsState(g.size.heightDp.dp, spec, label = "pillHeight")
    val corner by animateDpAsState(g.size.cornerDp.dp, spec, label = "pillCorner")

    val media = rememberLatest(ui.media)
    val peek = rememberLatest(ui.peek)
    val mediaAccent = remember(media?.accentArgb, s.accentIntensity) { readableAccent(media?.accentArgb, s.accentIntensity) }

    val pulse = remember { Animatable(0f) }
    LaunchedEffect(ui.peek?.key, reduced) {
        if (ui.peek != null && !reduced) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(1000))
        }
    }

    val latestUi = rememberUpdatedState(ui)
    val latestActions = rememberUpdatedState(actions)
    val borderLevel = s.borderIntensity / 100f

    val key = PanelKey(
        kind = ui.kind,
        card = if (ui.kind == PillKind.EXPANDED || ui.kind == PillKind.COMPACT) ui.card else null,
        lyrics = ui.kind == PillKind.EXPANDED && ui.lyricsOpen,
        token = if (ui.kind == PillKind.PEEK || ui.kind == PillKind.PEEK_CALL) ui.peek?.key ?: peek?.key.orEmpty() else "",
    )

    Box(
        Modifier
            .size(width, height)
            .graphicsLayer {
                alpha = shown
                val k = 0.84f + 0.16f * shown
                scaleX = k
                scaleY = k
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .clip(RoundedCornerShape(corner))
            .background(Color.Black.copy(alpha = s.opacityPercent / 100f))
            .drawWithContent {
                drawContent()
                val p = pulse.value
                if (borderLevel > 0f || p > 0f) {
                    val stroke = 1.dp.toPx()
                    val radius = corner.toPx().coerceAtMost(size.minDimension / 2f)
                    val crimson = max(0.9f * borderLevel, 0.9f * p)
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.07f + 0.10f * borderLevel + 0.12f * p),
                                NovaColors.Crimson.copy(alpha = crimson),
                            ),
                        ),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(max(0f, radius - stroke / 2f)),
                        style = Stroke(stroke),
                    )
                }
            }
            .then(if (interactive) Modifier.pillGestures(latestUi, latestActions) else Modifier),
    ) {
        Crossfade(
            targetState = key,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(if (reduced) 80 else 220),
            label = "panel",
        ) { k ->
            PanelFrame(k, ui, media, peek, mediaAccent, actions)
        }
        if (ui.kind == PillKind.EXPANDED) NoticeChip(ui.notice)
    }
}

@Composable
private fun PanelFrame(
    key: PanelKey,
    ui: IslandUi,
    media: MediaUi?,
    peek: PeekPayload?,
    mediaAccent: Color,
    actions: IslandActions,
) {
    val g = ui.geometry
    val s = ui.settings
    val screen = remember(g.screenWidthDp, g.screenHeightDp, g.landscape) {
        ScreenInfo(g.screenWidthDp, g.screenHeightDp, 1f, g.landscape, 0f, null)
    }
    val request = LayoutRequest(
        kind = key.kind,
        card = key.card,
        lyricsOpen = key.lyrics,
        showVolume = key.card == CardKind.MEDIA && s.volumeInMusicCard,
        showProgress = s.showProgress && (media?.durationMs ?: 0L) > 0L,
        cardCount = ui.cards.size,
        idleInfo = s.idleInfo,
        peekTall = peek?.body != null,
        cutoutWidthDp = g.cutoutWidthDp,
    )
    val natural = IslandLayout.size(request, s, screen)
    val extra = if (key.kind == PillKind.EXPANDED) max(0f, g.contentTopInsetDp - EXPANDED_TOP_PADDING_DP) else 0f
    val panelHeight = if (key.kind == PillKind.EXPANDED) min(natural.heightDp + extra, g.screenHeightDp - 24f) else natural.heightDp
    val topPad = max(EXPANDED_TOP_PADDING_DP, g.contentTopInsetDp)
    val accent = if (key.card == CardKind.MEDIA) mediaAccent else NovaColors.Crimson

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.requiredSize(natural.widthDp.dp, panelHeight.dp)) {
            when (key.kind) {
                PillKind.IDLE -> IdlePanel(ui, natural.widthDp)
                PillKind.COMPACT -> CompactPanel(ui, key.card, media, natural.widthDp, accent)
                PillKind.PEEK -> PeekPanel(peek, actions)
                PillKind.PEEK_CALL -> CallPanel(peek, actions, ui.reducedMotion)
                PillKind.EXPANDED -> when (key.card) {
                    CardKind.MEDIA -> MediaPanel(ui, media, accent, actions, topPad)
                    CardKind.TIMER -> TimerPanel(ui, accent, actions, topPad)
                    CardKind.STOPWATCH -> StopwatchPanel(ui, accent, actions, topPad)
                    CardKind.RECORD -> RecordPanel(ui, accent, actions, topPad)
                    CardKind.TOOLS, null -> ToolsPanel(ui, accent, actions, topPad)
                }
            }
        }
    }
}

/** Short message chip pinned to the bottom of the expanded panel (for example "This player does not offer next"). */
@Composable
private fun NoticeChip(notice: String?) {
    val text = rememberLatest(notice)
    val visible by animateFloatAsState(if (notice != null) 1f else 0f, tween(180), label = "notice")
    if (text == null || visible < 0.01f) return
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .graphicsLayer {
                    alpha = visible
                    translationY = (1f - visible) * 8.dp.toPx()
                }
                .clip(CircleShape)
                .background(NovaColors.Surface3)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            PillText(text, NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 15.sp), color = NovaColors.Text1, align = TextAlign.Center)
        }
    }
}

/**
 * Tap, long press, swipe and "hold to keep the peek". The hold observer watches the Initial pass so it never
 * consumes anything; buttons inside the pill consume their own presses first, so none of these fire for them.
 */
private fun Modifier.pillGestures(
    uiState: androidx.compose.runtime.State<IslandUi>,
    actionsState: androidx.compose.runtime.State<IslandActions>,
): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            var held = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val down = event.changes.any { it.pressed }
                if (down != held) {
                    held = down
                    actionsState.value.peekHold(down)
                }
            }
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                val kind = uiState.value.kind
                if (kind == PillKind.IDLE || kind == PillKind.COMPACT) actionsState.value.haptic()
                actionsState.value.tap()
            },
            onLongPress = { actionsState.value.longPress() },
        )
    }
    .pointerInput(Unit) {
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
            },
            onDrag = { change, amount ->
                change.consume()
                dx += amount.x
                dy += amount.y
            },
            onDragEnd = {
                val ui = uiState.value
                val actions = actionsState.value
                if (!ui.settings.swipeGestures) return@detectDragGestures
                val horizontal = 36.dp.toPx()
                val vertical = 24.dp.toPx()
                when {
                    abs(dx) > abs(dy) && abs(dx) > horizontal -> {
                        if (ui.cards.size > 1 && (ui.kind == PillKind.COMPACT || ui.kind == PillKind.EXPANDED)) {
                            actions.haptic()
                            actions.swipeCard(if (dx < 0f) 1 else -1)
                        }
                    }
                    dy > vertical -> actions.expand()
                    dy < -vertical -> when (ui.kind) {
                        PillKind.EXPANDED -> actions.collapse()
                        PillKind.PEEK, PillKind.PEEK_CALL -> actions.peekDismiss()
                        else -> Unit
                    }
                }
            },
        )
    }

/**
 * Reposition mode: the window covers the screen, the pill is drawn at its current place plus the drag so far,
 * and a finger anywhere moves it. "Done" saves, "Reset" restores the default place.
 */
@Composable
private fun RepositionLayer(ui: IslandUi, actions: IslandActions, modifier: Modifier) {
    val g = ui.geometry
    val density = LocalDensity.current.density
    val latestActions = rememberUpdatedState(actions)
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .pointerInput(density) {
                detectDragGestures { change, amount ->
                    change.consume()
                    latestActions.value.repositionBy(amount.x / density, amount.y / density)
                }
            },
    ) {
        Box(Modifier.offset(x = (g.placement.leftDp + g.dragDxDp).dp, y = (g.placement.topDp + g.dragDyDp).dp)) {
            Pill(ui, actions, interactive = false)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = (g.placement.topDp + g.dragDyDp + g.size.heightDp + 20f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PillText("Drag anywhere to move the pill", NovaType.Body.copy(fontSize = 14.sp), color = NovaColors.Text1, align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Reset", { actions.repositionReset() }, height = 48.dp, fontSize = 14)
                PillButton("Done", { actions.repositionDone() }, container = NovaColors.Crimson, content = Color.White, height = 48.dp, fontSize = 14)
            }
        }
    }
}
