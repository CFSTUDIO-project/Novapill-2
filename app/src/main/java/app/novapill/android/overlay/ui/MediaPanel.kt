package app.novapill.android.overlay.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.core.MediaPlayState
import app.novapill.android.core.TimeFormat
import app.novapill.android.media.MediaUi
import app.novapill.android.media.TransportActions
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandUi
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import kotlinx.coroutines.delay

/**
 * Expanded music card. Row heights add up to what [app.novapill.android.overlay.IslandLayout] reserves:
 * header (artwork size), 10, progress 40, controls 56, then lyrics 96 or volume 44, and the rest is the
 * bottom margin (plus the page dots when several cards are active).
 */
@Composable
internal fun MediaPanel(ui: IslandUi, media: MediaUi?, accent: Color, actions: IslandActions, topPadDp: Float) {
    val s = ui.settings
    val compactLandscape = ui.geometry.landscape && s.lsAutoAdapt && s.lsCompact
    val art = (if (compactLandscape) minOf(s.artworkSizeDp, 44) else s.artworkSizeDp).dp
    val hasProgress = s.showProgress && (media?.durationMs ?: 0L) > 0L
    val cardIndex = ui.cards.indexOf(CardKind.MEDIA).coerceAtLeast(0)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        MediaHeader(ui, media, accent, art)
        Spacer(Modifier.height(10.dp))
        if (hasProgress && media != null) ProgressRow(media, accent, actions)
        ControlsRow(ui, media, accent, actions)
        if (ui.lyricsOpen) {
            LyricsPanel(ui, media, accent, actions, Modifier.fillMaxWidth().height(96.dp))
        } else if (s.volumeInMusicCard) {
            VolumeRow(ui, actions, Modifier.fillMaxWidth().height(44.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, cardIndex, accent)
        }
    }
}

@Composable
private fun MediaHeader(ui: IslandUi, media: MediaUi?, accent: Color, art: Dp) {
    val s = ui.settings
    val playing = media?.state == MediaPlayState.PLAYING
    Row(Modifier.fillMaxWidth().height(art), verticalAlignment = Alignment.CenterVertically) {
        if (s.showArtwork) {
            Box(Modifier.accentGlow(accent, if (playing) s.accentIntensity / 100f else 0f)) {
                Artwork(media, art, 10.dp)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            if (art >= 52.dp) {
                PillText((media?.appLabel ?: "").uppercase(), NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp))
            }
            if (s.showTitleArtist) {
                PillText(media?.title?.ifBlank { null } ?: "Nothing playing", NovaType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp))
                val artist = media?.artist.orEmpty()
                if (artist.isNotBlank()) PillText(artist, NovaType.Body.copy(fontSize = 13.sp, lineHeight = 17.sp))
            }
        }
        if (s.visualizerEnabled) {
            Spacer(Modifier.width(8.dp))
            Visualizer(playing, accent, s.visualizerIntensity, ui.reducedMotion, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ProgressRow(media: MediaUi, accent: Color, actions: IslandActions) {
    val playing = media.state == MediaPlayState.PLAYING
    val now = rememberTicker(playing, 250L).value
    val duration = media.durationMs
    val position = media.playback.positionAt(now, duration).coerceIn(0L, duration)
    val live = if (duration > 0L) position.toFloat() / duration.toFloat() else 0f
    var drag by remember { mutableStateOf<Float?>(null) }
    var held by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(held) {
        if (held != null) {
            delay(900)
            held = null
        }
    }
    val fraction = drag ?: held ?: live
    val shownMs = (fraction * duration).toLong()
    val canSeek = TransportActions.canSeek(media.playback.actions)

    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        PillText(TimeFormat.trackTime(shownMs), NovaType.NumberSmall, Modifier.width(40.dp))
        PillSlider(
            fraction = fraction,
            onChange = { drag = it },
            onFinished = { f ->
                drag = null
                held = f
                actions.seekTo((f * duration).toLong())
            },
            onCancel = { drag = null },
            enabled = canSeek,
            accent = accent,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        PillText(TimeFormat.trackTime(duration), NovaType.NumberSmall, Modifier.width(40.dp), align = TextAlign.End)
    }
}

@Composable
private fun ControlsRow(ui: IslandUi, media: MediaUi?, accent: Color, actions: IslandActions) {
    val playbackActions = media?.playback?.actions ?: 0L
    val playing = media?.state == MediaPlayState.PLAYING || media?.state == MediaPlayState.BUFFERING
    val canToggle = media != null && (if (playing) TransportActions.canPause(playbackActions) else TransportActions.canPlay(playbackActions))
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(
            icon = NovaIcon.LYRICS,
            label = if (ui.lyricsOpen) "Hide lyrics" else "Show lyrics",
            onClick = { actions.toggleLyrics() },
            size = 44.dp,
            tint = if (ui.lyricsOpen) accent else NovaColors.Text2,
            enabled = media?.hasTitle == true,
        )
        RoundButton(
            icon = NovaIcon.PREVIOUS,
            label = "Previous track",
            onClick = { actions.previous() },
            enabled = media != null && TransportActions.canPrevious(playbackActions),
            iconSize = 24.dp,
        )
        Box(
            Modifier
                .size(56.dp)
                .pressable(enabled = canToggle, label = if (playing) "Pause" else "Play", pressedScale = 0.92f, onClick = { actions.playPause() })
                .clip(CircleShape)
                .background(if (canToggle) Color.White else Color.White.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = playing, animationSpec = tween(if (ui.reducedMotion) 60 else 160), label = "playIcon") { isPlaying ->
                NovaIconView(if (isPlaying) NovaIcon.PAUSE else NovaIcon.PLAY, Color.Black, size = 26.dp)
            }
        }
        RoundButton(
            icon = NovaIcon.NEXT,
            label = "Next track",
            onClick = { actions.next() },
            enabled = media != null && TransportActions.canNext(playbackActions),
            iconSize = 24.dp,
        )
        RoundButton(
            icon = NovaIcon.OPEN,
            label = "Open player",
            onClick = { actions.openPlayer() },
            size = 44.dp,
            tint = NovaColors.Text2,
            enabled = media != null,
        )
    }
}

@Composable
private fun VolumeRow(ui: IslandUi, actions: IslandActions, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        NovaIconView(NovaIcon.VOLUME_LOW, NovaColors.Text3, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        PillSlider(
            fraction = ui.tools.volume.fraction,
            onChange = { actions.setVolume(it) },
            onFinished = { actions.setVolume(it) },
            enabled = ui.tools.volume.max > 0,
            accent = Color.White,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Spacer(Modifier.width(10.dp))
        NovaIconView(NovaIcon.VOLUME, NovaColors.Text3, size = 18.dp)
    }
}
