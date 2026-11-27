package app.novapill.android.core

import app.novapill.android.settings.RecQuality
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class RecPhase { IDLE, PREPARING, RECORDING, STOPPING, SAVED, ERROR }

enum class RecError { CONSENT_DENIED, START_FAILED, ENCODER_FAILED, STORAGE_FAILED, EMPTY_FILE }

/** Things worth telling the user that are not failures. */
enum class RecNote { MIC_SKIPPED, STOPPED_BY_SYSTEM, LOWER_QUALITY_USED }

/**
 * What the screen recorder looks like from outside. The timer of the UI is derived from [startedAtMs],
 * which is only set once the encoder has really started, so a number is never counting without a recording.
 */
data class RecorderUi(
    val phase: RecPhase = RecPhase.IDLE,
    /** Seconds still to wait before recording starts (0 when not counting down). */
    val countdownLeft: Int = 0,
    /** SystemClock.elapsedRealtime() at the moment the recorder started; 0 otherwise. */
    val startedAtMs: Long = 0L,
    val withMic: Boolean = false,
    val error: RecError? = null,
    val note: RecNote? = null,
    val savedName: String? = null,
    val savedUri: String? = null,
) {
    /** A start, a recording or a stop is in progress. */
    val busy: Boolean get() = phase == RecPhase.PREPARING || phase == RecPhase.RECORDING || phase == RecPhase.STOPPING

    /** The recorder has something to say (anything but idle). */
    val visible: Boolean get() = phase != RecPhase.IDLE
}

/** Pure transitions. An impossible transition returns the state unchanged, so a late callback cannot corrupt it. */
object RecorderMachine {
    /** The user asked to record; the system consent dialog comes next. */
    fun requested(cur: RecorderUi): RecorderUi =
        if (cur.busy) cur else RecorderUi(phase = RecPhase.PREPARING)

    fun consentDenied(cur: RecorderUi): RecorderUi =
        if (cur.phase == RecPhase.PREPARING) cur.copy(phase = RecPhase.ERROR, error = RecError.CONSENT_DENIED, countdownLeft = 0) else cur

    /** Consent granted: counting down (0 = start at once), microphone yes/no as finally decided. */
    fun consentGranted(cur: RecorderUi, countdownSec: Int, withMic: Boolean): RecorderUi =
        if (cur.phase == RecPhase.PREPARING) cur.copy(countdownLeft = max(0, countdownSec), withMic = withMic) else cur

    fun countdown(cur: RecorderUi, left: Int): RecorderUi =
        if (cur.phase == RecPhase.PREPARING) cur.copy(countdownLeft = max(0, left)) else cur

    fun started(cur: RecorderUi, nowMs: Long): RecorderUi =
        if (cur.phase == RecPhase.PREPARING) cur.copy(phase = RecPhase.RECORDING, startedAtMs = nowMs, countdownLeft = 0) else cur

    /** Stop pressed. Before the encoder started there is nothing to save, so it simply ends. */
    fun stopping(cur: RecorderUi): RecorderUi = when (cur.phase) {
        RecPhase.RECORDING -> cur.copy(phase = RecPhase.STOPPING)
        RecPhase.PREPARING -> RecorderUi()
        else -> cur
    }

    fun saved(cur: RecorderUi, name: String, uri: String): RecorderUi =
        if (cur.phase == RecPhase.STOPPING || cur.phase == RecPhase.RECORDING) {
            cur.copy(phase = RecPhase.SAVED, savedName = name, savedUri = uri, startedAtMs = 0L, error = null)
        } else {
            cur
        }

    fun failed(cur: RecorderUi, error: RecError): RecorderUi =
        if (cur.phase == RecPhase.IDLE) cur else cur.copy(phase = RecPhase.ERROR, error = error, startedAtMs = 0L, countdownLeft = 0)

    fun withNote(cur: RecorderUi, note: RecNote): RecorderUi = if (cur.phase == RecPhase.IDLE) cur else cur.copy(note = note)

    fun dismissed(cur: RecorderUi): RecorderUi =
        if (cur.phase == RecPhase.SAVED || cur.phase == RecPhase.ERROR) RecorderUi() else cur
}

data class VideoProfile(val width: Int, val height: Int, val fps: Int, val bitrate: Int, val dpi: Int)

object RecordingProfile {
    private const val BITS_PER_PIXEL = 0.09
    private const val MIN_BITRATE = 2_000_000L
    private const val MAX_BITRATE = 24_000_000L

    /** Scales the screen so that its short side is [quality], never up, with even sizes (H.264 needs them). */
    fun compute(screenW: Int, screenH: Int, dpi: Int, quality: RecQuality, fps: Int): VideoProfile {
        val w0 = max(screenW, 2)
        val h0 = max(screenH, 2)
        val short = min(w0, h0)
        val target = min(quality.shortSide, short)
        val scale = target.toDouble() / short.toDouble()
        val w = even((w0 * scale).roundToInt())
        val h = even((h0 * scale).roundToInt())
        val f = if (fps >= 60) 60 else 30
        val bitrate = (w.toLong() * h.toLong() * f * BITS_PER_PIXEL).toLong().coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()
        return VideoProfile(w, h, f, bitrate, dpi.coerceIn(120, 640))
    }

    /** A gentler profile to retry with when the encoder refuses the first one, or null when already at the floor. */
    fun fallback(screenW: Int, screenH: Int, dpi: Int, quality: RecQuality, fps: Int): VideoProfile? =
        if (quality == RecQuality.P720 && fps < 60) null else compute(screenW, screenH, dpi, RecQuality.P720, 30)

    private fun even(v: Int): Int = max(v - (v % 2), 2)
}

object RecordingNames {
    const val FOLDER = "NOVA-PILL"

    fun fileName(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        format.timeZone = zone
        return "NOVA-PILL_${format.format(Date(epochMs))}.mp4"
    }
}
