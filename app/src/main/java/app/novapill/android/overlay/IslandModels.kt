package app.novapill.android.overlay

import app.novapill.android.core.CardKind
import app.novapill.android.core.CountdownState
import app.novapill.android.core.RecorderUi
import app.novapill.android.core.StopwatchState
import app.novapill.android.lyrics.LyricsState
import app.novapill.android.media.MediaUi
import app.novapill.android.notif.PeekPayload
import app.novapill.android.settings.NovaSettings
import app.novapill.android.util.BatteryInfo
import app.novapill.android.util.DeviceStatus
import app.novapill.android.util.RecordMethod
import app.novapill.android.util.SystemAppKind
import app.novapill.android.util.TorchState
import app.novapill.android.util.VolumeLevel
import app.novapill.android.util.WeatherState

/**
 * What the record card shows. The real screen recorder ([capture]) wins over everything else; then the manual
 * clock; then the OS signal.
 */
data class RecordUi(
    val method: RecordMethod,
    /** Manual clock, or null when the indicator comes from the OS signal. */
    val manual: StopwatchState?,
    /** Monotonic instant counting started for the OS signal. */
    val osSince: Long,
    /** State of NOVA PILL's own screen recorder, or null when it is idle. */
    val capture: RecorderUi? = null,
)

data class ToolsUi(
    /** What the camera framework last reported for the torch. Never assumed, see [TorchState]. */
    val torch: TorchState,
    val battery: BatteryInfo,
    val volume: VolumeLevel,
    val weather: WeatherState,
    val use24h: Boolean,
    val recordManualEnabled: Boolean,
    /** Quick panel page: 0 = Quick, 1 = More. */
    val page: Int = 0,
    val device: DeviceStatus = DeviceStatus.UNKNOWN,
    val calculatorOpen: Boolean = false,
    /** The screen recorder is preparing, recording or stopping. */
    val recorderBusy: Boolean = false,
) {
    val flashAvailable: Boolean get() = torch.available
    val flashOn: Boolean get() = torch.on
}

/** Where and how big the pill is, in dp, for the current frame of state. */
data class PillGeometry(
    val size: PillSize,
    val placement: Placement,
    /** Extra room above the content of a tall panel so it clears the camera hole. */
    val contentTopInsetDp: Float,
    val landscape: Boolean,
    val screenWidthDp: Float,
    val screenHeightDp: Float,
    val dragDxDp: Float = 0f,
    val dragDyDp: Float = 0f,
    /** Width of a roughly centred camera hole at the top, or 0 when there is none. */
    val cutoutWidthDp: Float = 0f,
)

/** The single immutable description of everything the island draws. The Compose UI renders only this. */
data class IslandUi(
    val visible: Boolean,
    val kind: PillKind,
    val card: CardKind?,
    val cards: List<CardKind>,
    val badges: List<CardKind>,
    val media: MediaUi?,
    val peek: PeekPayload?,
    val timer: CountdownState,
    val stopwatch: StopwatchState,
    val record: RecordUi?,
    val tools: ToolsUi,
    val lyricsOpen: Boolean,
    val lyrics: LyricsState,
    val lyricsOffsetMs: Int,
    val settings: NovaSettings,
    val reducedMotion: Boolean,
    val geometry: PillGeometry,
    val repositioning: Boolean,
    val notice: String?,
    /** True for the dashboard preview: the pill then labels itself "Preview". */
    val isPreview: Boolean = false,
)

/** Every user action the island can trigger. The real implementation is [IslandCoordinator]. */
interface IslandActions {
    fun tap()
    fun longPress()
    fun expand()
    fun collapse()
    fun swipeCard(delta: Int)
    fun outsideTouch()
    fun haptic()

    fun playPause()
    fun previous()
    fun next()
    fun seekTo(positionMs: Long)
    fun openPlayer()

    fun toggleLyrics()
    fun retryLyrics()
    fun importLyrics()
    fun chooseLyrics(index: Int)
    fun nudgeLyricsOffset(deltaMs: Int)
    fun resetLyricsOffset()

    fun setVolume(fraction: Float)

    fun startTimer(minutes: Int)
    fun pauseTimer()
    fun resumeTimer()
    fun cancelTimer()
    fun addTimerMinute()

    fun stopwatchStart()
    fun stopwatchPause()
    fun stopwatchResume()
    fun stopwatchReset()

    fun recordStart()
    fun recordPause()
    fun recordResume()
    fun recordStop()
    fun recordReset()
    fun recordDismiss()

    fun toggleFlashlight()
    fun openSystemApp(kind: SystemAppKind)
    fun openApp(packageName: String)
    fun setToolsPage(index: Int)

    /** Starts the real screen recorder (system consent dialog first) or stops it when it is running. */
    fun toggleScreenRecording()
    fun recorderOpenSaved()
    fun recorderDismiss()
    fun toggleCalculator()

    fun peekTap()
    fun peekAnswer()
    fun peekDecline()
    fun peekAction(index: Int)
    fun peekReply()
    fun peekDismiss()
    fun peekHold(held: Boolean)

    fun repositionBy(dxDp: Float, dyDp: Float)
    fun repositionDone()
    fun repositionReset()
}

/** Base for the dashboard preview: every action does nothing unless the preview overrides it. */
open class NoopIslandActions : IslandActions {
    override fun tap() {}
    override fun longPress() {}
    override fun expand() {}
    override fun collapse() {}
    override fun swipeCard(delta: Int) {}
    override fun outsideTouch() {}
    override fun haptic() {}
    override fun playPause() {}
    override fun previous() {}
    override fun next() {}
    override fun seekTo(positionMs: Long) {}
    override fun openPlayer() {}
    override fun toggleLyrics() {}
    override fun retryLyrics() {}
    override fun importLyrics() {}
    override fun chooseLyrics(index: Int) {}
    override fun nudgeLyricsOffset(deltaMs: Int) {}
    override fun resetLyricsOffset() {}
    override fun setVolume(fraction: Float) {}
    override fun startTimer(minutes: Int) {}
    override fun pauseTimer() {}
    override fun resumeTimer() {}
    override fun cancelTimer() {}
    override fun addTimerMinute() {}
    override fun stopwatchStart() {}
    override fun stopwatchPause() {}
    override fun stopwatchResume() {}
    override fun stopwatchReset() {}
    override fun recordStart() {}
    override fun recordPause() {}
    override fun recordResume() {}
    override fun recordStop() {}
    override fun recordReset() {}
    override fun recordDismiss() {}
    override fun toggleFlashlight() {}
    override fun openSystemApp(kind: SystemAppKind) {}
    override fun openApp(packageName: String) {}
    override fun setToolsPage(index: Int) {}
    override fun toggleScreenRecording() {}
    override fun recorderOpenSaved() {}
    override fun recorderDismiss() {}
    override fun toggleCalculator() {}
    override fun peekTap() {}
    override fun peekAnswer() {}
    override fun peekDecline() {}
    override fun peekAction(index: Int) {}
    override fun peekReply() {}
    override fun peekDismiss() {}
    override fun peekHold(held: Boolean) {}
    override fun repositionBy(dxDp: Float, dyDp: Float) {}
    override fun repositionDone() {}
    override fun repositionReset() {}
}
