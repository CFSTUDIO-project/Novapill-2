package app.novapill.android.ui.screens

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.novapill.android.NovaGraph
import app.novapill.android.core.CardKind
import app.novapill.android.core.CountdownMath
import app.novapill.android.core.CountdownState
import app.novapill.android.core.MediaPlayState
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.TorchStatus
import app.novapill.android.core.StopwatchState
import app.novapill.android.core.TimerPhase
import app.novapill.android.lyrics.LyricsState
import app.novapill.android.lyrics.LyricsUnavailable
import app.novapill.android.media.MediaUi
import app.novapill.android.media.PlaybackInfo
import app.novapill.android.media.TransportActions
import app.novapill.android.notif.PeekPayload
import app.novapill.android.overlay.IslandLayout
import app.novapill.android.overlay.IslandUi
import app.novapill.android.overlay.LayoutRequest
import app.novapill.android.overlay.NoopIslandActions
import app.novapill.android.overlay.Placement
import app.novapill.android.overlay.PillGeometry
import app.novapill.android.overlay.PillKind
import app.novapill.android.overlay.ScreenInfo
import app.novapill.android.overlay.ToolsUi
import app.novapill.android.overlay.ui.IslandRoot
import app.novapill.android.settings.ClockFormat
import app.novapill.android.settings.NovaSettings
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.util.BatteryInfo
import app.novapill.android.util.DeviceStatus
import app.novapill.android.util.NetworkKind
import app.novapill.android.util.TorchState
import app.novapill.android.util.WeatherInfo
import app.novapill.android.util.VolumeLevel
import app.novapill.android.util.WeatherState
import kotlinx.coroutines.delay

/**
 * State behind the dashboard and welcome previews. It renders the real pill through [IslandRoot], but every
 * value is a clearly labelled sample: it never reads your media or notifications, and its buttons only change
 * this preview (the flashlight button does not touch the real torch).
 */
class PreviewModel(private val appContext: Context) : NoopIslandActions() {
    var kind by mutableStateOf(PillKind.COMPACT)
    var card by mutableStateOf(CardKind.MEDIA)
    var playing by mutableStateOf(true)
    var lyricsOpen by mutableStateOf(false)
    var volume by mutableFloatStateOf(0.6f)
    var timer by mutableStateOf(CountdownState())
    var stopwatch by mutableStateOf(StopwatchState())
    var flashOn by mutableStateOf(false)
    var toolsPage by mutableIntStateOf(0)
    var peek by mutableStateOf<PeekPayload?>(null)

    private var basePosition by mutableLongStateOf(64_000L)
    private var baseElapsed by mutableLongStateOf(SystemClock.elapsedRealtime())
    private var peekCounter = 0

    private fun now(): Long = SystemClock.elapsedRealtime()

    val cards: List<CardKind>
        get() = buildList {
            add(CardKind.MEDIA)
            if (timer.phase != TimerPhase.NONE) add(CardKind.TIMER)
            if (stopwatch.phase != StopwatchPhase.NONE) add(CardKind.STOPWATCH)
            add(CardKind.TOOLS)
        }

    private fun positionNow(): Long =
        if (playing) (basePosition + (now() - baseElapsed)).coerceIn(0L, DURATION_MS) else basePosition

    fun showPeek(call: Boolean) {
        val noop = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(NOOP_ACTION).setPackage(appContext.packageName),
            PendingIntent.FLAG_IMMUTABLE,
        )
        peekCounter++
        peek = PeekPayload(
            key = "preview-$peekCounter",
            packageName = appContext.packageName,
            appLabel = if (call) "Phone" else "Preview",
            summary = if (call) "Sample caller" else "Sample notification",
            isCall = call,
            contentIntent = noop,
            answer = if (call) noop else null,
            decline = if (call) noop else null,
            actions = emptyList(),
            sender = if (call) "" else "Sample contact",
            body = if (call) null else "This is a sample message, not a real one.",
            canReply = !call,
        )
        kind = if (call) PillKind.PEEK_CALL else PillKind.PEEK
    }

    private fun endPeek() {
        peek = null
        kind = PillKind.COMPACT
    }

    fun peekLifetimeMs(): Long = if (peek?.isCall == true) 6_000L else 3_200L

    // ---- actions that change only the preview ----

    override fun tap() {
        when (kind) {
            PillKind.IDLE, PillKind.COMPACT -> kind = PillKind.EXPANDED
            PillKind.EXPANDED -> collapse()
            PillKind.PEEK, PillKind.PEEK_CALL -> endPeek()
        }
    }

    override fun expand() {
        if (kind == PillKind.COMPACT || kind == PillKind.IDLE) kind = PillKind.EXPANDED
    }

    override fun collapse() {
        kind = PillKind.COMPACT
        lyricsOpen = false
    }

    override fun swipeCard(delta: Int) {
        val all = cards
        val i = all.indexOf(card).coerceAtLeast(0)
        card = all[((i + delta) % all.size + all.size) % all.size]
        lyricsOpen = false
    }

    override fun playPause() {
        val pos = positionNow()
        basePosition = pos
        baseElapsed = now()
        playing = !playing
    }

    override fun seekTo(positionMs: Long) {
        basePosition = positionMs.coerceIn(0L, DURATION_MS)
        baseElapsed = now()
    }

    override fun toggleLyrics() {
        lyricsOpen = !lyricsOpen
    }

    override fun setVolume(fraction: Float) {
        volume = fraction.coerceIn(0f, 1f)
    }

    override fun startTimer(minutes: Int) {
        timer = CountdownMath.start(minutes * 60_000L, now())
        card = CardKind.TIMER
    }

    override fun pauseTimer() {
        timer = CountdownMath.pause(timer, now())
    }

    override fun resumeTimer() {
        timer = CountdownMath.resume(timer, now())
    }

    override fun cancelTimer() {
        timer = CountdownMath.cancel()
        if (card == CardKind.TIMER) card = CardKind.MEDIA
    }

    override fun addTimerMinute() {
        timer = CountdownMath.addTime(timer, 60_000L, now())
    }

    override fun stopwatchStart() {
        if (stopwatch.phase == StopwatchPhase.NONE) stopwatch = StopwatchMath.start(now())
        card = CardKind.STOPWATCH
    }

    override fun stopwatchPause() {
        stopwatch = StopwatchMath.pause(stopwatch, now())
    }

    override fun stopwatchResume() {
        stopwatch = StopwatchMath.resume(stopwatch, now())
    }

    override fun stopwatchReset() {
        stopwatch = StopwatchMath.reset()
        if (card == CardKind.STOPWATCH) card = CardKind.MEDIA
    }

    override fun toggleFlashlight() {
        flashOn = !flashOn
    }

    override fun setToolsPage(index: Int) {
        toolsPage = index.coerceIn(0, 1)
    }

    override fun peekReply() = endPeek()

    override fun peekTap() = endPeek()
    override fun peekAnswer() = endPeek()
    override fun peekDecline() = endPeek()
    override fun peekDismiss() = endPeek()

    /** Advances a running preview timer so it can reach "finished". */
    fun tickTimer() {
        if (timer.phase == TimerPhase.RUNNING) timer = CountdownMath.tick(timer, now())
    }

    fun build(settings: NovaSettings, widthDp: Float, use24h: Boolean): IslandUi {
        val screen = ScreenInfo(widthDp, SCREEN_HEIGHT_DP, 1f, false, 0f, null)
        val all = cards
        val effectiveCard: CardKind? = if (kind == PillKind.IDLE) null else if (card in all) card else CardKind.MEDIA
        val lyricsOn = lyricsOpen && kind == PillKind.EXPANDED && effectiveCard == CardKind.MEDIA
        val request = LayoutRequest(
            kind = kind,
            card = effectiveCard,
            lyricsOpen = lyricsOn,
            showVolume = effectiveCard == CardKind.MEDIA && settings.volumeInMusicCard,
            showProgress = settings.showProgress,
            cardCount = all.size,
            idleInfo = settings.idleInfo,
            peekTall = peek?.body != null,
        )
        val size = IslandLayout.size(request, settings, screen)
        val placement = Placement(
            leftDp = (widthDp - size.widthDp) / 2f,
            topDp = 0f,
            centerXDp = widthDp / 2f,
            cutout = null,
        )
        val position = positionNow()
        val media = MediaUi(
            packageName = appContext.packageName,
            appLabel = "Preview",
            title = "Sample track",
            artist = "Preview artist",
            album = "",
            durationMs = DURATION_MS,
            artwork = null,
            accentArgb = NovaColors.Crimson.toArgb(),
            playback = PlaybackInfo(
                state = if (playing) MediaPlayState.PLAYING else MediaPlayState.PAUSED,
                basePositionMs = position,
                speed = 1f,
                baseElapsedMs = now(),
                actions = TransportActions.PLAY or TransportActions.PAUSE or TransportActions.SKIP_TO_NEXT or
                    TransportActions.SKIP_TO_PREVIOUS or TransportActions.SEEK_TO,
            ),
        )
        return IslandUi(
            visible = true,
            kind = kind,
            card = effectiveCard,
            cards = all,
            badges = emptyList(),
            media = media,
            peek = peek,
            timer = timer,
            stopwatch = stopwatch,
            record = null,
            tools = ToolsUi(
                torch = TorchState(if (flashOn) TorchStatus.ON else TorchStatus.OFF),
                battery = BatteryInfo(percent = 82, charging = false, known = true),
                volume = VolumeLevel((volume * 15f).toInt(), 15),
                // A labelled sample: the preview never calls the weather service.
                weather = WeatherState.Ready(
                    WeatherInfo("Sample city", 27.0, 2, true, System.currentTimeMillis(), "preview"),
                    stale = false,
                ),
                use24h = use24h,
                recordManualEnabled = settings.recordManualEnabled,
                page = toolsPage,
                device = DeviceStatus(NetworkKind.WIFI, 12_400_000_000L),
            ),
            lyricsOpen = lyricsOn,
            lyrics = LyricsState.Unavailable("preview", LyricsUnavailable.NO_TRACK),
            lyricsOffsetMs = 0,
            settings = settings,
            reducedMotion = settings.reducedMotion,
            geometry = PillGeometry(
                size = size,
                placement = placement,
                contentTopInsetDp = 0f,
                landscape = false,
                screenWidthDp = widthDp,
                screenHeightDp = SCREEN_HEIGHT_DP,
            ),
            repositioning = false,
            notice = null,
            isPreview = true,
        )
    }

    private companion object {
        const val DURATION_MS = 214_000L
        const val SCREEN_HEIGHT_DP = 760f
        const val NOOP_ACTION = "app.novapill.android.PREVIEW_NOOP"
    }
}

/** The real pill renderer, fed by a [PreviewModel] and the live settings (so Appearance changes show at once). */
@Composable
fun PreviewIsland(model: PreviewModel, modifier: Modifier = Modifier, height: Dp = 340.dp) {
    val settings by NovaGraph.settings.settings.collectAsState()
    val context = LocalContext.current
    val use24h = when (settings.clockFormat) {
        ClockFormat.H24 -> true
        ClockFormat.H12 -> false
        ClockFormat.SYSTEM -> remember { DateFormat.is24HourFormat(context) }
    }
    LaunchedEffect(model.peek?.key) {
        if (model.peek != null) {
            delay(model.peekLifetimeMs())
            if (model.peek != null) {
                model.peek = null
                model.kind = PillKind.COMPACT
            }
        }
    }
    LaunchedEffect(model.timer.phase) {
        while (model.timer.phase == TimerPhase.RUNNING) {
            delay(250)
            model.tickTimer()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        IslandRoot(model.build(settings, maxWidth.value, use24h), model)
    }
}
