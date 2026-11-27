package app.novapill.android.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.text.format.DateFormat
import app.novapill.android.ImportLrcActivity
import app.novapill.android.NovaGraph
import app.novapill.android.R
import app.novapill.android.core.CardKind
import app.novapill.android.core.IslandStateMachine
import app.novapill.android.core.MediaPlayState
import app.novapill.android.core.RecPhase
import app.novapill.android.core.StateInputs
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.TimerPhase
import app.novapill.android.lyrics.LyricsState
import app.novapill.android.lyrics.LyricsSync
import app.novapill.android.lyrics.TrackMatcher
import app.novapill.android.media.MediaUi
import app.novapill.android.notif.QuickReplyActivity
import app.novapill.android.overlay.ui.IslandHost
import app.novapill.android.settings.ClockFormat
import app.novapill.android.settings.IdleInfo
import app.novapill.android.settings.NovaSettings
import app.novapill.android.settings.NovaSettingsCodec
import app.novapill.android.util.DeviceStatus
import app.novapill.android.util.Freeform
import app.novapill.android.util.FreeformOutcome
import app.novapill.android.util.IntentLauncher
import app.novapill.android.util.RecordMethod
import app.novapill.android.util.SystemAppKind
import app.novapill.android.util.SystemApps
import app.novapill.android.util.TorchError
import app.novapill.android.util.TorchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The brain behind the overlay. It watches every engine, feeds the pure [IslandStateMachine], turns the
 * result plus a few UI-only flags (expanded, selected card, lyrics panel, reposition mode) into one
 * [IslandUi], and tells the window controller how big the window must be. All of it runs on the main thread.
 */
class IslandCoordinator(
    private val context: Context,
    private val window: IslandWindowController,
) : IslandActions {
    private val g = NovaGraph
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    // Read by initialUi(), so they must be initialised before _ui.
    private var toolsPage = 0
    private var deviceStatus = DeviceStatus.UNKNOWN

    private val _ui = MutableStateFlow(initialUi())
    val ui: StateFlow<IslandUi> = _ui.asStateFlow()

    // UI-only state
    private var expanded = false
    private var userCard: CardKind? = null
    private var lyricsOpen = false
    private var toolsRequested = false
    private var recordDismissed = false
    private var repositioning = false
    private var dragDx = 0f
    private var dragDy = 0f
    private var notice: String? = null
    private var lastCapturePhase = RecPhase.IDLE
    private var lastTorchError: TorchError? = null

    // bookkeeping for the state machine
    private var mediaInactiveSince: Long? = null
    private var lastMediaState = MediaPlayState.NONE
    private var lastTrackId: String? = null
    private var idleSince: Long? = null
    private var prevVisible = false
    private var prevPeekKey: String? = null
    private var lyricsTrackId: String? = null
    private var toolsWereShown = false
    private var recheckJob: Job? = null
    private var noticeJob: Job? = null
    private var repositionJob: Job? = null
    private var started = false

    fun start(): Boolean {
        if (started) return true
        started = true
        g.notifications.start(scope)
        g.media.start(scope)
        g.foreground.start(scope)
        g.flashlight.start()
        val ok = window.attach(outsideTouch = { outsideTouch() }) {
            IslandHost(this@IslandCoordinator)
        }
        if (!ok) {
            stop()
            return false
        }
        scope.launch { for (ignored in trigger) recompute() }
        observe(g.settings.settings)
        observe(g.media.current)
        observe(g.notifications.current)
        observe(g.clocks.timer)
        observe(g.clocks.stopwatch)
        observe(g.record.manual)
        observe(g.record.osVisible)
        observe(g.flashlight.state)
        observe(g.recorder.state)
        observe(g.floating.calculatorOpen)
        observe(g.system.screenOn)
        observe(g.system.powerSave)
        observe(g.foreground.foreground)
        observe(window.screen)
        observe(window.systemUi)
        observe(g.lyrics.state)
        observe(g.volume.level)
        observe(g.battery.info)
        observe(g.weather.state)
        trigger.trySend(Unit)
        return true
    }

    fun stop() {
        if (!started) return
        started = false
        recheckJob?.cancel()
        noticeJob?.cancel()
        repositionJob?.cancel()
        g.lyrics.release()
        g.notifications.stop()
        g.media.stop()
        g.foreground.stop()
        g.flashlight.releaseIfOwned()
        g.flashlight.stop()
        g.floating.closeAll()
        window.detach()
        scope.cancel()
    }

    /** Called when the display changed (rotation, fold, density): re-read metrics, then relayout. */
    fun onConfigurationChanged() {
        window.refreshScreen()
        g.floating.onConfigurationChanged()
        trigger.trySend(Unit)
    }

    private fun <T> observe(flow: Flow<T>) {
        scope.launch { flow.collect { trigger.trySend(Unit) } }
    }

    private fun initialUi(): IslandUi {
        val s = NovaSettings()
        val screen = ScreenInfo.DEFAULT
        val size = IslandLayout.size(LayoutRequest(PillKind.IDLE), s, screen)
        return IslandUi(
            visible = false,
            kind = PillKind.IDLE,
            card = null,
            cards = emptyList(),
            badges = emptyList(),
            media = null,
            peek = null,
            timer = g.clocks.timer.value,
            stopwatch = g.clocks.stopwatch.value,
            record = null,
            tools = toolsUi(s, false),
            lyricsOpen = false,
            lyrics = LyricsState.Idle,
            lyricsOffsetMs = 0,
            settings = s,
            reducedMotion = false,
            geometry = PillGeometry(size, IslandLayout.placement(size, s, screen), 0f, false, screen.widthDp, screen.heightDp),
            repositioning = false,
            notice = null,
        )
    }

    private fun toolsUi(s: NovaSettings, use24h: Boolean) = ToolsUi(
        torch = g.flashlight.state.value,
        battery = g.battery.info.value,
        volume = g.volume.level.value,
        weather = g.weather.state.value,
        use24h = use24h,
        recordManualEnabled = s.recordManualEnabled,
        page = toolsPage,
        device = deviceStatus,
        calculatorOpen = g.floating.calculatorOpen.value,
        recorderBusy = g.recorder.state.value.busy,
    )

    // ---- the reducer: engines in, one IslandUi out ----

    private fun recompute() {
        if (!started) return
        val s = g.settings.settings.value
        val now = SystemClock.elapsedRealtime()
        val screen = window.screen.value
        val sysUi = window.systemUi.value
        val media = g.media.current.value
        val peekEntry = g.notifications.current.value
        val peek = peekEntry?.payload
        val timer = g.clocks.timer.value
        val stopwatch = g.clocks.stopwatch.value
        val torch = g.flashlight.state.value
        val flashOn = torch.on && s.flashShowInPill
        if (torch.error != lastTorchError) {
            // Only the late "the system never confirmed" case is announced here; the immediate results of a tap are announced by the tap.
            if (torch.error == TorchError.NOT_CONFIRMED) showNotice(context.getString(R.string.flash_not_confirmed))
            lastTorchError = torch.error
        }
        val reduced = s.reducedMotion || g.system.systemAnimationsOff() || (s.reduceMotionOnBatterySaver && g.system.powerSave.value)
        val use24h = when (s.clockFormat) {
            ClockFormat.H24 -> true
            ClockFormat.H12 -> false
            ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        }

        // media inactivity (paused or stopped) for the grace period
        val mediaState = media?.state ?: MediaPlayState.NONE
        val inactive = mediaState == MediaPlayState.PAUSED || mediaState == MediaPlayState.STOPPED
        val wasInactive = lastMediaState == MediaPlayState.PAUSED || lastMediaState == MediaPlayState.STOPPED
        if (inactive) {
            if (mediaInactiveSince == null || !wasInactive || media?.trackId != lastTrackId) mediaInactiveSince = now
        } else {
            mediaInactiveSince = null
        }
        lastMediaState = mediaState
        lastTrackId = media?.trackId
        val inactiveFor = mediaInactiveSince?.let { now - it } ?: 0L

        // recording: NOVA PILL's own recorder wins, then the manual clock, then the OS signal
        val capture = g.recorder.state.value
        if (capture.phase != lastCapturePhase) {
            lastCapturePhase = capture.phase
            val news = capture.phase == RecPhase.PREPARING || capture.phase == RecPhase.SAVED || capture.phase == RecPhase.ERROR
            if (news) userCard = CardKind.RECORD
        }
        val manual = g.record.manual.value
        val manualActive = s.recordManualEnabled && manual.phase != StopwatchPhase.NONE
        val osActive = s.recordUseOs && g.record.osSupported && g.record.osVisible.value
        if (!osActive) recordDismissed = false
        val captureActive = capture.visible
        val recordActive = captureActive || manualActive || (osActive && !recordDismissed)
        if (!recordActive && userCard == CardKind.RECORD) userCard = null
        val recordUi = when {
            !recordActive -> null
            captureActive -> RecordUi(RecordMethod.APP, null, 0L, capture)
            manualActive -> RecordUi(RecordMethod.MANUAL, manual, 0L)
            else -> RecordUi(RecordMethod.OS, null, g.record.osSince.value)
        }

        // things that hide the island entirely
        val foregroundApp = g.foreground.foreground.value
        val hiddenByApp = foregroundApp != null && foregroundApp in s.hiddenApps
        val hiddenByFullscreen = sysUi.fullscreen && (s.hideInFullscreen || (screen.landscape && s.hideInLandscapeFullscreen))
        val hiddenByKeyboard = sysUi.imeVisible && s.hideWithKeyboard
        // Optional: keep the pill out of the video while the screen is really being recorded.
        val hiddenByCapture = s.recHidePill && capture.phase == RecPhase.RECORDING
        val hiddenBySystem = !g.system.screenOn.value || hiddenByApp || hiddenByFullscreen || hiddenByKeyboard || hiddenByCapture

        // a new peek takes over the pill; an open panel collapses first
        if (peek != null && peek.key != prevPeekKey) {
            expanded = false
            lyricsOpen = false
        }
        prevPeekKey = peek?.key

        val peekActive = peek != null && !hiddenBySystem
        if (timer.phase == TimerPhase.FINISHED) userCard = null

        val inputs = StateInputs(
            serviceRunning = true,
            media = mediaState,
            mediaInactiveForMs = inactiveFor,
            peekActive = peekActive,
            recordingActive = recordActive,
            timer = timer.phase,
            stopwatch = stopwatch.phase,
            flashOn = flashOn,
            toolsRequested = toolsRequested,
            idleForMs = idleSince?.let { now - it } ?: 0L,
            idleTimeoutMs = s.idleTimeoutSec * 1000L,
            pausedGraceMs = s.pauseGraceSec * 1000L,
            pauseBehavior = s.pauseBehavior,
            hideAfterMediaStops = s.hideAfterMediaStops,
            showMinimalIdlePill = s.showMinimalIdlePill,
        )
        val out = IslandStateMachine.resolve(inputs)

        if (out.primary != null) {
            idleSince = null
        } else if (idleSince == null) {
            // A peek arriving while the island was hidden must not leave an idle pill behind afterwards.
            idleSince = if (peekActive && !prevVisible) now - inputs.idleTimeoutMs else now
        }

        val visible = out.visible && !hiddenBySystem
        val card: CardKind? = userCard?.takeIf { it in out.cards } ?: out.primary
        if (!visible || card == null) expanded = false
        if (card != CardKind.MEDIA) lyricsOpen = false
        if (!expanded && !flashOn) toolsRequested = false

        val kind = when {
            peekActive && peek != null -> if (peek.isCall) PillKind.PEEK_CALL else PillKind.PEEK
            expanded && card != null -> PillKind.EXPANDED
            card == null -> PillKind.IDLE
            else -> PillKind.COMPACT
        }
        val lyricsOn = lyricsOpen && kind == PillKind.EXPANDED && card == CardKind.MEDIA && media != null && media.hasTitle

        updateLyricsRequest(lyricsOn, media?.trackId, media)
        val toolsShown = kind == PillKind.EXPANDED && card == CardKind.TOOLS
        if (toolsShown && !toolsWereShown) {
            // The panel just opened: read the real torch state again and refresh the two cheap facts it shows.
            toolsPage = 0
            deviceStatus = g.deviceStatus.read()
            g.flashlight.resync()
            g.weather.refresh()
        }
        if (kind == PillKind.IDLE && s.idleInfo == IdleInfo.FULL) g.weather.refresh()
        toolsWereShown = toolsShown
        if (toolsShown) g.volume.refresh()

        // geometry
        val cutoutWidthDp = screen.topCutout?.takeIf { abs(it.centerX - screen.widthDp / 2f) < CENTRED_CUTOUT_DP }?.width ?: 0f
        val showVolume = card == CardKind.MEDIA && s.volumeInMusicCard
        val showProgress = s.showProgress && (media?.durationMs ?: 0L) > 0L
        val request = LayoutRequest(
            kind = kind,
            card = card,
            lyricsOpen = lyricsOn,
            showVolume = showVolume,
            showProgress = showProgress,
            cardCount = out.cards.size,
            idleInfo = s.idleInfo,
            peekTall = peek?.body != null,
            cutoutWidthDp = cutoutWidthDp,
        )
        var size = IslandLayout.size(request, s, screen)
        var placement = IslandLayout.placement(size, s, screen)
        var topInset = 0f
        if (kind == PillKind.EXPANDED) {
            topInset = IslandLayout.contentTopInset(placement)
            val extra = max(0f, topInset - EXPANDED_TOP_PADDING_DP)
            if (extra > 0f) {
                size = size.copy(heightDp = min(size.heightDp + extra, screen.heightDp - 24f))
                placement = IslandLayout.placement(size, s, screen)
            }
        }
        val geometry = PillGeometry(
            size = size,
            placement = placement,
            contentTopInsetDp = topInset,
            landscape = screen.landscape,
            screenWidthDp = screen.widthDp,
            screenHeightDp = screen.heightDp,
            dragDxDp = dragDx,
            dragDyDp = dragDy,
            cutoutWidthDp = cutoutWidthDp,
        )

        val trackKey = media?.lyricsKey
        _ui.value = IslandUi(
            visible = visible,
            kind = kind,
            card = card,
            cards = if (visible) out.cards else emptyList(),
            badges = out.badges,
            media = media,
            peek = if (peekActive) peek else null,
            timer = timer,
            stopwatch = stopwatch,
            record = recordUi,
            tools = toolsUi(s, use24h),
            lyricsOpen = lyricsOn,
            lyrics = g.lyrics.state.value,
            lyricsOffsetMs = s.offsetFor(trackKey),
            settings = s,
            reducedMotion = reduced,
            geometry = geometry,
            repositioning = repositioning && visible,
            notice = notice,
        )
        prevVisible = visible

        window.apply(
            WindowSpec(
                visible = visible,
                fullscreen = repositioning && visible,
                widthDp = size.widthDp,
                heightDp = size.heightDp,
                leftDp = placement.leftDp,
                topDp = placement.topDp,
                watchOutside = kind == PillKind.EXPANDED,
                shrinkDelayMs = if (reduced) 240L else 560L,
            ),
        )

        recheckJob?.cancel()
        val wait = out.recheckInMs
        if (wait != null) {
            recheckJob = scope.launch {
                delay(wait + 20L)
                trigger.trySend(Unit)
            }
        }
    }

    private fun updateLyricsRequest(lyricsOn: Boolean, trackId: String?, media: MediaUi?) {
        if (lyricsOn && media != null) {
            if (trackId != lyricsTrackId) {
                lyricsTrackId = trackId
                g.lyrics.request(TrackMatcher.Query(media.title, media.artist, media.album, media.durationMs))
            }
        } else if (lyricsTrackId != null) {
            lyricsTrackId = null
            g.lyrics.release()
        }
    }

    private fun settings(): NovaSettings = g.settings.settings.value

    private fun refresh() {
        trigger.trySend(Unit)
    }

    private fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(2_600L)
            notice = null
            refresh()
        }
        refresh()
    }

    // ---- surface ----

    override fun tap() {
        if (repositioning) return
        val ui = _ui.value
        val s = settings()
        when (ui.kind) {
            PillKind.IDLE -> if (s.tapToExpand) {
                toolsRequested = true
                userCard = CardKind.TOOLS
                expanded = true
            }
            PillKind.COMPACT -> if (s.tapToExpand) openPanel()
            PillKind.EXPANDED -> collapse()
            PillKind.PEEK, PillKind.PEEK_CALL -> peekTap()
        }
        refresh()
    }

    private fun openPanel() {
        expanded = true
        lyricsOpen = settings().autoOpenLyrics && _ui.value.card == CardKind.MEDIA
    }

    override fun longPress() {
        val s = settings()
        if (!s.longPressReposition || repositioning) return
        val ui = _ui.value
        if (!ui.visible || ui.kind == PillKind.PEEK_CALL) return
        repositioning = true
        expanded = false
        lyricsOpen = false
        dragDx = 0f
        dragDy = 0f
        haptic()
        armRepositionTimeout()
        refresh()
    }

    override fun expand() {
        if (repositioning) return
        val ui = _ui.value
        if (!ui.visible || ui.kind == PillKind.EXPANDED || ui.kind == PillKind.PEEK || ui.kind == PillKind.PEEK_CALL) return
        if (ui.kind == PillKind.IDLE) {
            toolsRequested = true
            userCard = CardKind.TOOLS
            expanded = true
        } else {
            openPanel()
        }
        refresh()
    }

    override fun collapse() {
        expanded = false
        lyricsOpen = false
        if (!g.flashlight.state.value.on) toolsRequested = false
        refresh()
    }

    override fun swipeCard(delta: Int) {
        val ui = _ui.value
        val cards = ui.cards
        val current = ui.card ?: return
        val i = cards.indexOf(current)
        if (i < 0 || cards.size < 2) return
        val next = cards[((i + delta) % cards.size + cards.size) % cards.size]
        userCard = next
        lyricsOpen = false
        if (next == CardKind.TOOLS) toolsRequested = true
        refresh()
    }

    override fun outsideTouch() {
        if (_ui.value.kind == PillKind.EXPANDED) collapse()
    }

    override fun haptic() {
        if (settings().haptics) window.haptic()
    }

    // ---- media ----

    override fun playPause() {
        haptic()
        if (!g.media.togglePlayPause()) showNotice("This player does not offer play or pause")
    }

    override fun previous() {
        haptic()
        if (!g.media.previous()) showNotice("This player does not offer previous")
    }

    override fun next() {
        haptic()
        if (!g.media.next()) showNotice("This player does not offer next")
    }

    override fun seekTo(positionMs: Long) {
        if (!g.media.seekTo(positionMs)) showNotice("This player does not allow seeking")
    }

    override fun openPlayer() {
        if (g.media.openPlayer()) collapse() else showNotice("The player could not be opened")
    }

    // ---- lyrics ----

    override fun toggleLyrics() {
        lyricsOpen = !lyricsOpen
        refresh()
    }

    override fun retryLyrics() {
        g.lyrics.retry()
    }

    override fun importLyrics() {
        val ok = IntentLauncher.start(context, Intent(context, ImportLrcActivity::class.java))
        if (!ok) showNotice("Could not open the file picker")
    }

    override fun chooseLyrics(index: Int) {
        val state = g.lyrics.state.value
        if (state is LyricsState.Pick) state.options.getOrNull(index)?.let { g.lyrics.choose(it) }
    }

    override fun nudgeLyricsOffset(deltaMs: Int) {
        val key = g.media.current.value?.lyricsKey ?: return
        val current = settings().offsetFor(key)
        val next = LyricsSync.clampOffset(NovaSettingsCodec.snapOffset(current + deltaMs))
        g.settings.update { st ->
            val map = st.trackOffsets.toMutableMap()
            if (key !in map && map.size >= NovaSettings.MAX_TRACK_OFFSETS) map.remove(map.keys.first())
            map[key] = next
            st.copy(trackOffsets = map)
        }
    }

    override fun resetLyricsOffset() {
        val key = g.media.current.value?.lyricsKey ?: return
        g.settings.update { st -> st.copy(trackOffsets = st.trackOffsets - key) }
    }

    // ---- tools ----

    override fun setVolume(fraction: Float) {
        g.volume.set(fraction)
    }

    override fun startTimer(minutes: Int) {
        g.clocks.startTimer(minutes * 60_000L)
        userCard = CardKind.TIMER
        refresh()
    }

    override fun pauseTimer() = g.clocks.pauseTimer()
    override fun resumeTimer() = g.clocks.resumeTimer()

    override fun cancelTimer() {
        g.clocks.cancelTimer()
        if (userCard == CardKind.TIMER) userCard = null
        refresh()
    }

    override fun addTimerMinute() = g.clocks.addTime(60_000L)

    override fun stopwatchStart() {
        g.clocks.stopwatchStart()
        userCard = CardKind.STOPWATCH
        refresh()
    }

    override fun stopwatchPause() = g.clocks.stopwatchPause()
    override fun stopwatchResume() = g.clocks.stopwatchResume()

    override fun stopwatchReset() {
        g.clocks.stopwatchReset()
        if (userCard == CardKind.STOPWATCH) userCard = null
        refresh()
    }

    override fun recordStart() {
        g.record.manualStart()
        userCard = CardKind.RECORD
        refresh()
    }

    override fun recordPause() = g.record.manualPause()
    override fun recordResume() = g.record.manualResume()

    override fun recordStop() {
        g.record.manualStop()
        if (userCard == CardKind.RECORD) userCard = null
        refresh()
    }

    override fun recordReset() = g.record.manualReset()

    override fun recordDismiss() {
        recordDismissed = true
        if (userCard == CardKind.RECORD) userCard = null
        refresh()
    }

    override fun toggleFlashlight() {
        haptic()
        when (g.flashlight.toggle(settings().flashStrengthPercent)) {
            TorchResult.SENT -> Unit
            TorchResult.NO_FLASH -> showNotice(context.getString(R.string.flash_none))
            TorchResult.BUSY -> showNotice(context.getString(R.string.flash_busy))
            TorchResult.REFUSED -> showNotice(context.getString(R.string.flash_refused))
            TorchResult.FAILED -> showNotice(context.getString(R.string.flash_failed))
        }
    }

    override fun setToolsPage(index: Int) {
        toolsPage = index.coerceIn(0, 1)
        refresh()
    }

    override fun toggleScreenRecording() {
        haptic()
        if (g.recorder.state.value.busy) {
            g.recorder.requestStop()
        } else if (!g.recorder.requestStart()) {
            showNotice(context.getString(R.string.rec_cannot_start))
            return
        }
        // The system consent dialog (or the saving step) must not be covered by an open panel.
        collapse()
    }

    override fun recorderOpenSaved() {
        val raw = g.recorder.state.value.savedUri ?: return
        val uri = Uri.parse(raw)
        if (uri.scheme != "content") {
            // Android 8 and 9 keep the file in the app's own movie folder; there is no shareable address for it.
            showNotice(context.getString(R.string.rec_saved_folder_only))
            return
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (IntentLauncher.start(context, view)) {
            g.recorder.dismiss()
            collapse()
        } else {
            showNotice(context.getString(R.string.rec_no_viewer))
        }
    }

    override fun recorderDismiss() {
        g.recorder.dismiss()
        refresh()
    }

    override fun toggleCalculator() {
        haptic()
        val s = settings()
        if (!s.floatingTools) {
            showNotice(context.getString(R.string.floating_off))
            return
        }
        val wasOpen = g.floating.calculatorOpen.value
        if (!g.floating.toggleCalculator(s)) {
            showNotice(context.getString(R.string.floating_failed))
            return
        }
        if (!wasOpen) collapse() else refresh()
    }

    override fun openSystemApp(kind: SystemAppKind) {
        if (SystemApps.open(context, kind)) collapse() else showNotice("No app found for this action")
    }

    override fun openApp(packageName: String) {
        val intent = g.appInfo.launchIntent(packageName)
        if (intent == null) {
            showNotice(context.getString(R.string.app_open_failed))
            return
        }
        val s = settings()
        if (!s.floatingExternal) {
            if (IntentLauncher.start(context, intent)) collapse() else showNotice(context.getString(R.string.app_open_failed))
            return
        }
        // "Open in a floating window" is a request to Android, and the answer is reported as it is.
        val screen = window.screen.value
        val density = context.resources.displayMetrics.density
        val outcome = Freeform.launch(
            context,
            intent,
            s.floatingWidthPct,
            s.floatingHeightPct,
            (screen.widthDp * density).roundToInt(),
            (screen.heightDp * density).roundToInt(),
        )
        when (outcome) {
            FreeformOutcome.FREEFORM_REQUESTED -> {
                collapse()
                showNotice(context.getString(R.string.freeform_requested))
            }
            FreeformOutcome.OPENED_NORMALLY -> {
                collapse()
                showNotice(context.getString(R.string.freeform_unsupported))
            }
            FreeformOutcome.FAILED -> showNotice(context.getString(R.string.app_open_failed))
        }
    }

    // ---- peek ----

    override fun peekTap() {
        val payload = _ui.value.peek ?: return
        IntentLauncher.send(context, payload.contentIntent)
        g.notifications.dismissCurrent()
    }

    override fun peekAnswer() {
        val payload = _ui.value.peek ?: return
        IntentLauncher.send(context, payload.answer)
        g.notifications.dismissCurrent()
    }

    override fun peekDecline() {
        val payload = _ui.value.peek ?: return
        IntentLauncher.send(context, payload.decline)
        g.notifications.dismissCurrent()
    }

    override fun peekAction(index: Int) {
        val payload = _ui.value.peek ?: return
        payload.actions.getOrNull(index)?.let { IntentLauncher.send(context, it.intent) }
        g.notifications.dismissCurrent()
    }

    override fun peekReply() {
        val payload = _ui.value.peek ?: return
        if (!payload.canReply) return
        val intent = QuickReplyActivity.intent(context, payload.key, payload.sender)
        if (IntentLauncher.start(context, intent)) {
            g.notifications.dismissCurrent()
        } else {
            showNotice(context.getString(R.string.reply_open_failed))
        }
    }

    override fun peekDismiss() {
        g.notifications.dismissCurrent()
    }

    override fun peekHold(held: Boolean) {
        g.notifications.hold(held)
    }

    // ---- reposition mode ----

    private fun armRepositionTimeout() {
        repositionJob?.cancel()
        repositionJob = scope.launch {
            delay(REPOSITION_TIMEOUT_MS)
            repositionDone()
        }
    }

    override fun repositionBy(dxDp: Float, dyDp: Float) {
        if (!repositioning) return
        val s = settings()
        val screen = window.screen.value
        val independent = screen.landscape && s.lsIndependent
        val baseX = s.effectiveXOffsetDp(screen.landscape)
        val baseTop = s.effectiveTopOffsetDp(screen.landscape)
        val maxX = if (independent) 400f else 300f
        dragDx = (baseX + dragDx + dxDp).coerceIn(-maxX, maxX) - baseX
        dragDy = (baseTop + dragDy + dyDp).coerceIn(-24f, 160f) - baseTop
        armRepositionTimeout()
        refresh()
    }

    override fun repositionDone() {
        if (!repositioning) return
        val s = settings()
        val screen = window.screen.value
        val independent = screen.landscape && s.lsIndependent
        val newX = (s.effectiveXOffsetDp(screen.landscape) + dragDx).roundToInt()
        val newTop = (s.effectiveTopOffsetDp(screen.landscape) + dragDy).roundToInt()
        g.settings.update { st ->
            if (independent) {
                st.copy(lsXOffsetDp = newX.coerceIn(-400, 400), lsTopOffsetDp = newTop.coerceIn(-24, 160))
            } else {
                st.copy(xOffsetDp = newX.coerceIn(-300, 300), topOffsetDp = newTop.coerceIn(-24, 160))
            }
        }
        exitReposition()
    }

    override fun repositionReset() {
        if (!repositioning) return
        val screen = window.screen.value
        val independent = screen.landscape && settings().lsIndependent
        g.settings.update { st ->
            if (independent) st.copy(lsXOffsetDp = 0, lsTopOffsetDp = 0) else st.copy(xOffsetDp = 0, topOffsetDp = 0)
        }
        exitReposition()
    }

    private fun exitReposition() {
        repositioning = false
        dragDx = 0f
        dragDy = 0f
        repositionJob?.cancel()
        refresh()
    }

    private companion object {
        const val EXPANDED_TOP_PADDING_DP = 16f
        const val REPOSITION_TIMEOUT_MS = 20_000L

        /** A camera hole this close to the middle counts as "centred" for the idle pill. */
        const val CENTRED_CUTOUT_DP = 48f
    }
}
