package app.novapill.android.overlay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.MediaPlayState
import app.novapill.android.core.TimeFormat
import app.novapill.android.lyrics.LyricLine
import app.novapill.android.lyrics.LyricsSource
import app.novapill.android.lyrics.LyricsState
import app.novapill.android.lyrics.LyricsSync
import app.novapill.android.lyrics.LyricsUnavailable
import app.novapill.android.lyrics.SyncKind
import app.novapill.android.media.MediaUi
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandUi
import app.novapill.android.settings.LyricsHighlight
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType

/**
 * The 96 dp lyrics area of the music card. Everything shown comes from a real lyric source: an imported
 * .LRC file or LRCLIB. Line sync is labelled "Line sync" and is never drawn as word sync; text without
 * timestamps is labelled "Unsynced lyrics" and never auto-scrolls.
 */
@Composable
internal fun LyricsPanel(ui: IslandUi, media: MediaUi?, accent: Color, actions: IslandActions, modifier: Modifier) {
    Column(modifier) {
        when (val state = ui.lyrics) {
            is LyricsState.Ready -> {
                Box(Modifier.fillMaxWidth().weight(1f)) { LyricsBody(state, ui, media, accent) }
                LyricsFooter(state, ui, actions)
            }
            is LyricsState.Pick -> LyricsPick(state, actions)
            is LyricsState.Unavailable -> LyricsMessage(state, actions)
            is LyricsState.Loading, LyricsState.Idle -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PillText("Loading lyrics…", NovaType.Caption.copy(fontSize = 12.sp), color = NovaColors.Text3)
            }
        }
    }
}

@Composable
private fun LyricsBody(state: LyricsState.Ready, ui: IslandUi, media: MediaUi?, accent: Color) {
    val s = ui.settings
    if (state.instrumental || state.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PillText(if (state.instrumental) "Instrumental" else "No lyric text", NovaType.Caption.copy(fontSize = 12.sp))
        }
        return
    }
    val lines = state.lines
    val synced = state.syncKind != SyncKind.NONE
    val rowHeight = (22f * s.lyricsSpacingPercent / 100f).dp
    val fontSize = (14f * s.lyricsFontPercent / 100f).sp
    val playing = media?.state == MediaPlayState.PLAYING

    val now = rememberTicker(synced && playing, 100L).value
    val position = media?.playback?.positionAt(now, media.durationMs) ?: 0L
    val lookup = LyricsSync.lookupPosition(position, ui.lyricsOffsetMs)
    val active = if (synced) LyricsSync.activeIndex(lines, lookup) else -1
    val activeColor = if (s.lyricsHighlight == LyricsHighlight.ACCENT) accent else Color.White

    val listState = rememberLazyListState()
    LaunchedEffect(active, synced, lines.size) {
        if (synced) {
            val target = active.coerceAtLeast(0)
            if (ui.reducedMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = rowHeight, bottom = rowHeight),
        userScrollEnabled = !synced,
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = synced && index == active
            LyricRow(
                line = line,
                isActive = isActive,
                synced = synced,
                activeWord = if (isActive && state.syncKind == SyncKind.WORD) LyricsSync.activeWordIndex(line, lookup) else -1,
                barProgress = if (isActive && s.lyricsHighlight == LyricsHighlight.BAR) LyricsSync.lineProgress(lines, index, lookup) else 0f,
                activeColor = activeColor,
                accent = accent,
                fontSize = fontSize,
                rowHeight = rowHeight,
                reduced = ui.reducedMotion,
            )
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    synced: Boolean,
    activeWord: Int,
    barProgress: Float,
    activeColor: Color,
    accent: Color,
    fontSize: TextUnit,
    rowHeight: Dp,
    reduced: Boolean,
) {
    val t by animateFloatAsState(if (isActive) 1f else 0f, tween(if (reduced) 0 else 260), label = "lyricActive")
    val idle = if (synced) NovaColors.Text3 else NovaColors.Text2
    val color = lerp(idle, activeColor, t)
    val style = TextStyle(
        fontSize = fontSize,
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
        color = color,
    )
    val words = line.words
    val text: AnnotatedString = if (isActive && words != null && activeWord >= 0) {
        buildAnnotatedString {
            words.forEachIndexed { i, w ->
                if (i > 0) append(" ")
                withStyle(SpanStyle(color = if (i <= activeWord) activeColor else NovaColors.Text3)) { append(w.text) }
            }
        }
    } else {
        AnnotatedString(line.text.ifBlank { "·" })
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .drawBehind {
                if (barProgress > 0f) {
                    val h = 2.dp.toPx()
                    drawRoundRect(
                        color = accent.copy(alpha = 0.9f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width * barProgress, h),
                        cornerRadius = CornerRadius(h / 2f, h / 2f),
                    )
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicText(
            text = text,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val k = 1f + 0.05f * t
                scaleX = k
                scaleY = k
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        )
    }
}

@Composable
private fun LyricsFooter(state: LyricsState.Ready, ui: IslandUi, actions: IslandActions) {
    val kind = when (state.syncKind) {
        SyncKind.WORD -> "Word sync"
        SyncKind.LINE -> "Line sync"
        SyncKind.NONE -> "Unsynced lyrics"
    }
    val source = when (state.source) {
        LyricsSource.IMPORTED -> "Imported file"
        LyricsSource.LRCLIB -> "LRCLIB"
    }
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        PillText("$kind · $source", NovaType.Caption.copy(fontSize = 11.sp), Modifier.weight(1f))
        if (state.syncKind != SyncKind.NONE) OffsetControls(ui.lyricsOffsetMs, actions)
    }
}

@Composable
private fun OffsetControls(offsetMs: Int, actions: IslandActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniChip(NovaIcon.MINUS, "Lyrics earlier by 50 milliseconds") { actions.nudgeLyricsOffset(-NovaSettings.OFFSET_STEP_MS) }
        val label = if (offsetMs == 0) "0 ms" else (if (offsetMs > 0) "+$offsetMs ms" else "$offsetMs ms")
        PillText(label, NovaType.NumberSmall.copy(fontSize = 11.sp), Modifier.width(58.dp), align = TextAlign.Center)
        MiniChip(NovaIcon.PLUS, "Lyrics later by 50 milliseconds") { actions.nudgeLyricsOffset(NovaSettings.OFFSET_STEP_MS) }
        Spacer(Modifier.width(6.dp))
        PillButton(
            text = "Reset",
            onClick = { actions.resetLyricsOffset() },
            enabled = offsetMs != 0,
            height = 32.dp,
            fontSize = 11,
        )
    }
}

@Composable
private fun MiniChip(icon: NovaIcon, label: String, onClick: () -> Unit) {
    RoundButton(
        icon = icon,
        label = label,
        onClick = onClick,
        size = 34.dp,
        container = NovaColors.Surface3,
        iconSize = 14.dp,
    )
}

@Composable
private fun LyricsMessage(state: LyricsState.Unavailable, actions: IslandActions) {
    val message = when (state.reason) {
        LyricsUnavailable.PROVIDER_OFF -> "Online lyrics are off. Import a .LRC file, or enable LRCLIB in Settings."
        LyricsUnavailable.NOT_FOUND -> "Lyrics unavailable for this track"
        LyricsUnavailable.NETWORK -> "Couldn't reach the lyrics service"
        LyricsUnavailable.ERROR -> "Lyrics unavailable for this track"
        LyricsUnavailable.NO_TRACK -> "No track playing"
    }
    val canRetry = state.reason == LyricsUnavailable.NOT_FOUND || state.reason == LyricsUnavailable.NETWORK || state.reason == LyricsUnavailable.ERROR
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        PillText(message, NovaType.Caption.copy(fontSize = 12.sp, lineHeight = 16.sp), color = NovaColors.Text2, maxLines = 2, align = TextAlign.Center)
        if (state.reason != LyricsUnavailable.NO_TRACK) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) PillButton("Retry", { actions.retryLyrics() }, icon = NovaIcon.RESET, height = 34.dp, fontSize = 12)
                PillButton("Import .LRC", { actions.importLyrics() }, icon = NovaIcon.UPLOAD, height = 34.dp, fontSize = 12)
            }
        }
    }
}

@Composable
private fun LyricsPick(state: LyricsState.Pick, actions: IslandActions) {
    Column(Modifier.fillMaxSize()) {
        PillText("Pick the matching version", NovaType.Caption.copy(fontSize = 11.sp), Modifier.height(20.dp), color = NovaColors.Text2)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.options) { index, option ->
                val c = option.candidate
                val duration = if (c.durationMs > 0L) " · ${TimeFormat.trackTime(c.durationMs)}" else ""
                val sync = if (c.hasSynced) " · synced" else " · plain"
                val text = "${c.title} — ${c.artist}$duration$sync"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .pressable(label = text, pressedScale = 0.98f, onClick = { actions.chooseLyrics(index) })
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillText(text, NovaType.BodyStrong.copy(fontSize = 12.sp, lineHeight = 16.sp))
                }
            }
        }
    }
}
