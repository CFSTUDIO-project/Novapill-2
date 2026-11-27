package app.novapill.android.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.core.CountdownMath
import app.novapill.android.core.RecError
import app.novapill.android.core.RecNote
import app.novapill.android.core.RecPhase
import app.novapill.android.core.RecorderUi
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.TimeFormat
import app.novapill.android.core.TimerPhase
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandUi
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.components.pressable
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import app.novapill.android.util.RecordMethod

// ---------------------------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TimerPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val timer = ui.timer
    val finished = timer.phase == TimerPhase.FINISHED
    val now = rememberTicker(timer.phase == TimerPhase.RUNNING, 100L).value
    val left = CountdownMath.remaining(timer, now)
    val progress = if (finished) 1f else CountdownMath.progress(timer, now)
    val blink = rememberBlink(finished && !ui.reducedMotion)
    val caption = when (timer.phase) {
        TimerPhase.RUNNING -> "Timer · running"
        TimerPhase.PAUSED -> "Timer · paused"
        TimerPhase.FINISHED -> "Time's up"
        TimerPhase.NONE -> "Timer"
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Ring(progress, accent, Modifier.size(64.dp))
                NovaIconView(NovaIcon.TIMER, NovaColors.Text2, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    TimeFormat.countdown(left),
                    NovaType.NumberBig,
                    color = if (finished) accent.copy(alpha = blink) else if (timer.phase == TimerPhase.PAUSED) NovaColors.Text2 else NovaColors.Text1,
                )
                PillText(caption, NovaType.Caption)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (timer.phase) {
                TimerPhase.RUNNING -> {
                    PillButton("Pause", { actions.pauseTimer() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                    RoundButton(NovaIcon.CLOSE, "Cancel timer", { actions.cancelTimer() }, container = NovaColors.Surface3)
                }
                TimerPhase.PAUSED -> {
                    PillButton("Resume", { actions.resumeTimer() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                    RoundButton(NovaIcon.CLOSE, "Cancel timer", { actions.cancelTimer() }, container = NovaColors.Surface3)
                }
                TimerPhase.FINISHED -> {
                    PillButton("Done", { actions.cancelTimer() }, Modifier.weight(1f), NovaIcon.CHECK, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    PillButton("+1 min", { actions.addTimerMinute() }, Modifier.weight(1f), NovaIcon.PLUS, height = 48.dp, fontSize = 14)
                }
                TimerPhase.NONE -> Unit
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.TIMER).coerceAtLeast(0), accent)
        }
    }
}

/** Ring that shows [fraction] (1 = full) clockwise from the top. */
@Composable
private fun Ring(fraction: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val arc = Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.14f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0.002f) {
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stopwatch
// ---------------------------------------------------------------------------------------------

@Composable
internal fun StopwatchPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val sw = ui.stopwatch
    val running = sw.phase == StopwatchPhase.RUNNING
    val now = rememberTicker(running, 100L).value
    val elapsed = StopwatchMath.elapsed(sw, now)
    val minuteFraction = (elapsed % 60_000L).toFloat() / 60_000f

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Ring(if (elapsed == 0L) 0f else minuteFraction.coerceAtLeast(0.004f), accent, Modifier.size(64.dp))
                NovaIconView(NovaIcon.STOPWATCH, NovaColors.Text2, size = 22.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    TimeFormat.stopwatch(elapsed),
                    NovaType.NumberBig,
                    color = if (sw.phase == StopwatchPhase.PAUSED) NovaColors.Text2 else NovaColors.Text1,
                )
                PillText(if (running) "Stopwatch · running" else "Stopwatch · paused", NovaType.Caption)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) {
                PillButton("Pause", { actions.stopwatchPause() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
            } else {
                PillButton("Resume", { actions.stopwatchResume() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
            }
            PillButton("Reset", { actions.stopwatchReset() }, Modifier.weight(1f), NovaIcon.RESET, height = 48.dp, fontSize = 14)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.STOPWATCH).coerceAtLeast(0), accent)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Record indicator
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RecordPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val capture = ui.record?.capture
    if (capture != null) {
        CapturePanel(ui, capture, accent, actions, topPadDp)
    } else {
        IndicatorPanel(ui, accent, actions, topPadDp)
    }
}

/** NOVA PILL's own screen recorder: countdown, recording, saving, and the saved or failed result. */
@Composable
private fun CapturePanel(ui: IslandUi, capture: RecorderUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val recording = capture.phase == RecPhase.RECORDING
    val now = rememberTicker(recording, 250L).value
    val elapsed = if (recording && capture.startedAtMs > 0L) (now - capture.startedAtMs).coerceAtLeast(0L) else 0L

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            when (capture.phase) {
                RecPhase.SAVED -> NovaIconView(NovaIcon.CHECK, NovaColors.Green, size = 20.dp)
                RecPhase.ERROR -> NovaIconView(NovaIcon.CLOSE, NovaColors.Danger, size = 20.dp)
                else -> RecordDot(ui.reducedMotion, size = 12, paused = !recording)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(captureTitle(capture), NovaType.Title.copy(fontSize = 15.sp, lineHeight = 19.sp))
                PillText(captureSubtitle(capture), NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp))
            }
            if (recording) {
                PillText(TimeFormat.hms(elapsed), NovaType.NumberBig.copy(fontSize = 22.sp, lineHeight = 26.sp), align = TextAlign.End)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (capture.phase) {
                RecPhase.RECORDING ->
                    PillButton("Stop", { actions.toggleScreenRecording() }, Modifier.weight(1f), NovaIcon.STOP, NovaColors.Danger, Color.White, height = 48.dp, fontSize = 14)
                RecPhase.PREPARING ->
                    if (capture.countdownLeft > 0) {
                        PillButton("Cancel", { actions.toggleScreenRecording() }, Modifier.weight(1f), NovaIcon.CLOSE, height = 48.dp, fontSize = 14)
                    } else {
                        PillButton("Waiting", {}, Modifier.weight(1f), enabled = false, height = 48.dp, fontSize = 14)
                    }
                RecPhase.STOPPING -> PillButton("Saving", {}, Modifier.weight(1f), enabled = false, height = 48.dp, fontSize = 14)
                RecPhase.SAVED -> {
                    if (capture.savedUri != null) {
                        PillButton("Open video", { actions.recorderOpenSaved() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    }
                    PillButton("Dismiss", { actions.recorderDismiss() }, Modifier.weight(1f), NovaIcon.CLOSE, height = 48.dp, fontSize = 14)
                }
                RecPhase.ERROR -> {
                    if (capture.error == RecError.CONSENT_DENIED) {
                        PillButton("Try again", { actions.toggleScreenRecording() }, Modifier.weight(1f), NovaIcon.RECORD, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                    }
                    PillButton("Dismiss", { actions.recorderDismiss() }, Modifier.weight(1f), NovaIcon.CLOSE, height = 48.dp, fontSize = 14)
                }
                RecPhase.IDLE -> Unit
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            PillText(captureFootnote(capture), NovaType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp), maxLines = 2)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.RECORD).coerceAtLeast(0), accent)
        }
    }
}

private fun captureTitle(c: RecorderUi): String = when (c.phase) {
    RecPhase.PREPARING -> if (c.countdownLeft > 0) "Starting in ${c.countdownLeft}" else "Waiting for Android"
    RecPhase.RECORDING -> "Recording the screen"
    RecPhase.STOPPING -> "Saving the video"
    RecPhase.SAVED -> "Video saved"
    RecPhase.ERROR -> when (c.error) {
        RecError.CONSENT_DENIED -> "Permission not granted"
        RecError.START_FAILED -> "Could not start"
        RecError.ENCODER_FAILED -> "Encoder failed"
        RecError.STORAGE_FAILED -> "Could not save"
        RecError.EMPTY_FILE -> "Recording was empty"
        null -> "Recording failed"
    }
    RecPhase.IDLE -> "Screen recorder"
}

private fun captureSubtitle(c: RecorderUi): String = when (c.phase) {
    RecPhase.PREPARING -> "SCREEN RECORDER"
    RecPhase.RECORDING -> if (c.withMic) "MICROPHONE ON" else "NO MICROPHONE"
    RecPhase.STOPPING -> "PLEASE WAIT"
    RecPhase.SAVED -> c.savedName ?: "NOVA-PILL"
    RecPhase.ERROR -> "NOTHING WAS SAVED"
    RecPhase.IDLE -> ""
}

private fun captureFootnote(c: RecorderUi): String {
    val note = when (c.note) {
        RecNote.MIC_SKIPPED -> "The microphone was not used. "
        RecNote.STOPPED_BY_SYSTEM -> "Android ended the recording. "
        RecNote.LOWER_QUALITY_USED -> "Recorded at a lower quality. "
        null -> ""
    }
    val main = when (c.phase) {
        RecPhase.PREPARING -> if (c.countdownLeft > 0) "Cancel to stop before it starts." else "Confirm screen capture in Android's dialog."
        RecPhase.RECORDING -> "Screen only. Sound from other apps is not captured."
        RecPhase.STOPPING -> "Finishing the file."
        RecPhase.SAVED ->
            if (c.savedUri?.startsWith("content:") == true) "Saved in Movies/NOVA-PILL." else "Saved in the app's Movies folder."
        RecPhase.ERROR -> when (c.error) {
            RecError.CONSENT_DENIED -> "You did not allow screen capture."
            RecError.START_FAILED -> "This phone could not start the recorder."
            RecError.ENCODER_FAILED -> "The video encoder stopped with an error."
            RecError.STORAGE_FAILED -> "The video could not be written to storage."
            RecError.EMPTY_FILE -> "The file had no video, so it was removed."
            null -> ""
        }
        RecPhase.IDLE -> ""
    }
    return note + main
}

@Composable
private fun IndicatorPanel(ui: IslandUi, accent: Color, actions: IslandActions, topPadDp: Float) {
    val record = ui.record
    val manual = record?.manual
    val isManual = record?.method == RecordMethod.MANUAL && manual != null
    val running = record != null && (record.method == RecordMethod.OS || manual?.phase == StopwatchPhase.RUNNING)
    val paused = isManual && manual?.phase == StopwatchPhase.PAUSED
    val now = rememberTicker(running && ui.settings.recordShowDuration, 250L).value
    val elapsed = if (record != null) recordElapsed(record, now) else 0L

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(topPadDp.dp))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            RecordDot(ui.reducedMotion, size = 12, paused = paused)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                PillText(
                    if (isManual) (if (paused) "Recording timer · paused" else "Recording timer") else "Screen recording visible",
                    NovaType.Title.copy(fontSize = 15.sp, lineHeight = 19.sp),
                )
                PillText(if (isManual) "MANUAL" else "SYSTEM SIGNAL", NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp))
            }
            if (ui.settings.recordShowDuration) {
                PillText(TimeFormat.hms(elapsed), NovaType.NumberBig.copy(fontSize = 22.sp, lineHeight = 26.sp), align = TextAlign.End)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isManual) {
                if (running) {
                    PillButton("Pause", { actions.recordPause() }, Modifier.weight(1f), NovaIcon.PAUSE, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                } else {
                    PillButton("Resume", { actions.recordResume() }, Modifier.weight(1f), NovaIcon.PLAY, Color.White, Color.Black, height = 48.dp, fontSize = 14)
                }
                PillButton("Stop", { actions.recordStop() }, Modifier.weight(1f), NovaIcon.DOT, NovaColors.Danger, Color.White, height = 48.dp, fontSize = 14)
                RoundButton(NovaIcon.RESET, "Reset recording timer", { actions.recordReset() }, container = NovaColors.Surface3)
            } else {
                PillButton("Dismiss", { actions.recordDismiss() }, Modifier.weight(1f), NovaIcon.CLOSE, height = 48.dp, fontSize = 14)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            PillText(
                if (isManual) "A timer you control. It is not connected to the system screen recorder."
                else "Reported by Android for this app's own windows only. Recordings of other apps are not detected.",
                NovaType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                maxLines = 2,
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (ui.cards.size > 1) CardDots(ui.cards.size, ui.cards.indexOf(CardKind.RECORD).coerceAtLeast(0), accent)
        }
    }
}
