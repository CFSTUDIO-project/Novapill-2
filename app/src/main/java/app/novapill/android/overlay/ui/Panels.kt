package app.novapill.android.overlay.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novapill.android.core.CardKind
import app.novapill.android.core.CountdownMath
import app.novapill.android.core.MediaPlayState
import app.novapill.android.core.RecPhase
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.TimeFormat
import app.novapill.android.core.TimerPhase
import app.novapill.android.media.MediaUi
import app.novapill.android.notif.PeekPayload
import app.novapill.android.overlay.IslandActions
import app.novapill.android.overlay.IslandLayout
import app.novapill.android.overlay.IslandUi
import app.novapill.android.overlay.RecordUi
import app.novapill.android.settings.IdleInfo
import app.novapill.android.settings.TempUnits
import app.novapill.android.util.BatteryInfo
import app.novapill.android.util.RecordMethod
import app.novapill.android.util.WeatherCodes
import app.novapill.android.util.WeatherState
import app.novapill.android.ui.components.NovaIcon
import app.novapill.android.ui.components.NovaIconView
import app.novapill.android.ui.theme.NovaColors
import app.novapill.android.ui.theme.NovaType
import java.util.Locale

private const val SIDE_PAD_DP = 10f

/**
 * The resting pill. What it shows is the user's choice (clock; clock and battery; clock, weather and battery).
 * With a camera hole in the middle the clock sits on one side of it and the rest on the other, so nothing is
 * ever drawn over the hole.
 */
@Composable
internal fun IdlePanel(ui: IslandUi, widthDp: Float) {
    val clock = rememberClockReading()
    val cutout = ui.geometry.placement.cutout
    val info = ui.settings.idleInfo
    val use24h = ui.tools.use24h
    if (cutout != null) {
        val gap = cutout.widthDp + IslandLayout.CUTOUT_GAP_DP
        val leftWidth = (widthDp / 2f + cutout.centerOffsetDp - gap / 2f - SIDE_PAD_DP).coerceAtLeast(0f)
        Row(
            Modifier.fillMaxSize().padding(horizontal = SIDE_PAD_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(leftWidth.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                IdleClock(clock, use24h)
            }
            Spacer(Modifier.width(gap.dp))
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                IdleStatus(ui, info)
            }
        }
    } else {
        Row(
            Modifier.fillMaxSize().padding(horizontal = SIDE_PAD_DP.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdleClock(clock, use24h)
            if (info != IdleInfo.CLOCK) {
                Spacer(Modifier.width(10.dp))
                IdleStatus(ui, info)
            }
        }
    }
}

@Composable
private fun IdleClock(clock: ClockReading, use24h: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PillText(TimeFormat.clock(clock.hour24, clock.minute, use24h), NovaType.NumberMid.copy(fontSize = 13.sp))
        if (!use24h) {
            Spacer(Modifier.width(3.dp))
            PillText(TimeFormat.meridiem(clock.hour24), NovaType.NumberSmall.copy(fontSize = 9.sp), color = NovaColors.Text3)
        }
    }
}

@Composable
private fun IdleStatus(ui: IslandUi, info: IdleInfo) {
    if (info == IdleInfo.CLOCK) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (info == IdleInfo.FULL) {
            IdleWeather(ui)
            Spacer(Modifier.width(8.dp))
        }
        IdleBattery(ui.tools.battery)
    }
}

/** Real temperature, dimmed when it is old, a dash when there is none. Never a placeholder number. */
@Composable
private fun IdleWeather(ui: IslandUi) {
    val fahrenheit = TempUnits.useFahrenheit(ui.settings.weatherUnit, Locale.getDefault().country)
    when (val w = ui.tools.weather) {
        is WeatherState.Ready -> {
            val tint = if (w.stale) NovaColors.Text3 else NovaColors.Text1
            NovaIconView(weatherIcon(WeatherCodes.kind(w.info.code, w.info.isDay)), tint, size = 14.dp)
            Spacer(Modifier.width(3.dp))
            PillText(WeatherCodes.formatTemp(w.info.tempC, fahrenheit), NovaType.NumberMid.copy(fontSize = 12.sp), color = tint)
        }
        WeatherState.Loading, WeatherState.Idle -> PillText("…", NovaType.NumberMid.copy(fontSize = 12.sp), color = NovaColors.Text4)
        is WeatherState.Unavailable -> {
            NovaIconView(NovaIcon.CLOUD, NovaColors.Text4, size = 14.dp)
            Spacer(Modifier.width(3.dp))
            PillText("–", NovaType.NumberMid.copy(fontSize = 12.sp), color = NovaColors.Text4)
        }
    }
}

@Composable
private fun IdleBattery(battery: BatteryInfo) {
    if (!battery.known) return
    val tint = when {
        battery.charging -> NovaColors.Green
        battery.percent <= 15 -> NovaColors.Danger
        else -> NovaColors.Text2
    }
    NovaIconView(if (battery.charging) NovaIcon.BOLT else NovaIcon.BATTERY, tint, size = 14.dp)
    Spacer(Modifier.width(2.dp))
    PillText("${battery.percent}", NovaType.NumberMid.copy(fontSize = 12.sp), color = tint)
}

/**
 * The collapsed pill. With a top camera hole the content is split into a left and a right cluster so
 * nothing is ever drawn over the hole; without one it is a single row with the status on the right.
 */
@Composable
internal fun CompactPanel(ui: IslandUi, card: CardKind?, media: MediaUi?, widthDp: Float, accent: Color) {
    val cutout = ui.geometry.placement.cutout
    val gap = if (cutout != null) cutout.widthDp + 14f else 0f
    val leftWidth = if (cutout != null) (widthDp / 2f + cutout.centerOffsetDp - gap / 2f - SIDE_PAD_DP).coerceAtLeast(0f) else 0f

    Row(
        Modifier.fillMaxSize().padding(horizontal = SIDE_PAD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cutout != null) {
            Box(Modifier.width(leftWidth.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                CompactLeft(ui, card, media, leftWidth, accent)
            }
            Spacer(Modifier.width(gap.dp))
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                CompactRight(ui, card, media, accent)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                CompactLeft(ui, card, media, widthDp, accent)
            }
            Spacer(Modifier.width(8.dp))
            CompactRight(ui, card, media, accent)
        }
    }
}

@Composable
private fun CompactLeft(ui: IslandUi, card: CardKind?, media: MediaUi?, roomDp: Float, accent: Color) {
    val s = ui.settings
    val art = (s.heightDp - 14).coerceAtLeast(18).dp
    when (card) {
        CardKind.MEDIA -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.showArtwork) Artwork(media, art, 7.dp)
            if (s.showTitleArtist && roomDp >= 92f && media != null) {
                Spacer(Modifier.width(8.dp))
                PillText(
                    media.title.ifBlank { media.appLabel },
                    NovaType.BodyStrong.copy(fontSize = 13.sp, lineHeight = 16.sp),
                )
            }
        }
        CardKind.TIMER -> NovaIconView(NovaIcon.TIMER, accent, size = 18.dp)
        CardKind.STOPWATCH -> NovaIconView(NovaIcon.STOPWATCH, accent, size = 18.dp)
        CardKind.RECORD -> Row(verticalAlignment = Alignment.CenterVertically) {
            val capture = ui.record?.capture
            when (capture?.phase) {
                RecPhase.SAVED -> NovaIconView(NovaIcon.CHECK, NovaColors.Green, size = 16.dp)
                RecPhase.ERROR -> NovaIconView(NovaIcon.CLOSE, NovaColors.Danger, size = 16.dp)
                else -> RecordDot(ui.reducedMotion, paused = capture != null && capture.phase != RecPhase.RECORDING)
            }
            if (roomDp >= 64f && capture?.phase != RecPhase.SAVED && capture?.phase != RecPhase.ERROR) {
                Spacer(Modifier.width(6.dp))
                PillText("REC", NovaType.Label.copy(fontSize = 10.sp), color = NovaColors.Danger)
            }
        }
        CardKind.TOOLS -> NovaIconView(NovaIcon.FLASHLIGHT, if (ui.tools.flashOn) NovaColors.Yellow else NovaColors.Text3, size = 18.dp)
        null -> Unit
    }
}

@Composable
private fun CompactRight(ui: IslandUi, card: CardKind?, media: MediaUi?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (badge in ui.badges) {
            NovaIconView(badgeIcon(badge), if (badge == CardKind.RECORD) NovaColors.Danger else NovaColors.Text3, size = 13.dp)
        }
        when (card) {
            CardKind.MEDIA -> if (ui.settings.visualizerEnabled) {
                Visualizer(
                    playing = media?.state == MediaPlayState.PLAYING,
                    color = accent,
                    intensityPercent = ui.settings.visualizerIntensity,
                    reduced = ui.reducedMotion,
                    modifier = Modifier.size(width = 16.dp, height = (ui.settings.heightDp - 20).coerceAtLeast(10).dp),
                )
            }
            CardKind.TIMER -> CompactTimerText(ui, accent)
            CardKind.STOPWATCH -> CompactStopwatchText(ui)
            CardKind.RECORD -> CompactRecordText(ui)
            // "On" only when Android has reported the torch as on. With the torch off this card is just "Tools".
            CardKind.TOOLS -> if (ui.tools.flashOn) {
                PillText("On", NovaType.NumberMid.copy(fontSize = 13.sp), color = NovaColors.Yellow)
            } else {
                PillText("Tools", NovaType.NumberMid.copy(fontSize = 13.sp), color = NovaColors.Text3)
            }
            null -> Unit
        }
    }
}

private fun badgeIcon(kind: CardKind): NovaIcon = when (kind) {
    CardKind.MEDIA -> NovaIcon.MUSIC
    CardKind.TIMER -> NovaIcon.TIMER
    CardKind.STOPWATCH -> NovaIcon.STOPWATCH
    CardKind.RECORD -> NovaIcon.DOT
    CardKind.TOOLS -> NovaIcon.FLASHLIGHT
}

@Composable
private fun CompactTimerText(ui: IslandUi, accent: Color) {
    val timer = ui.timer
    val now by rememberTicker(timer.phase == TimerPhase.RUNNING, 200L)
    val left = CountdownMath.remaining(timer, now)
    val blink = rememberBlink(timer.phase == TimerPhase.FINISHED && !ui.reducedMotion)
    PillText(
        text = TimeFormat.countdown(left),
        style = NovaType.NumberMid.copy(fontSize = 14.sp),
        color = if (timer.phase == TimerPhase.FINISHED) accent.copy(alpha = blink) else if (timer.phase == TimerPhase.PAUSED) NovaColors.Text3 else NovaColors.Text1,
    )
}

@Composable
private fun CompactStopwatchText(ui: IslandUi) {
    val sw = ui.stopwatch
    val now by rememberTicker(sw.phase == StopwatchPhase.RUNNING, 250L)
    val text = TimeFormat.stopwatch(StopwatchMath.elapsed(sw, now)).dropLast(2)
    PillText(text, NovaType.NumberMid.copy(fontSize = 14.sp), color = if (sw.phase == StopwatchPhase.PAUSED) NovaColors.Text3 else NovaColors.Text1)
}

@Composable
private fun CompactRecordText(ui: IslandUi) {
    val record = ui.record
    val capture = record?.capture
    val running = when {
        record == null -> false
        capture != null -> capture.phase == RecPhase.RECORDING
        else -> record.method == RecordMethod.OS || record.manual?.phase == StopwatchPhase.RUNNING
    }
    val now by rememberTicker(running && ui.settings.recordShowDuration, 500L)
    if (record == null) return
    if (capture != null) {
        when (capture.phase) {
            RecPhase.RECORDING ->
                if (ui.settings.recordShowDuration) PillText(clockText(recordElapsed(record, now)), NovaType.NumberMid.copy(fontSize = 14.sp))
            RecPhase.PREPARING ->
                PillText(if (capture.countdownLeft > 0) "${capture.countdownLeft}" else "…", NovaType.NumberMid.copy(fontSize = 14.sp))
            RecPhase.STOPPING -> PillText("Saving", NovaType.NumberMid.copy(fontSize = 13.sp), color = NovaColors.Text2)
            RecPhase.SAVED -> PillText("Saved", NovaType.NumberMid.copy(fontSize = 13.sp), color = NovaColors.Green)
            RecPhase.ERROR -> PillText("Failed", NovaType.NumberMid.copy(fontSize = 13.sp), color = NovaColors.Danger)
            RecPhase.IDLE -> Unit
        }
    } else if (ui.settings.recordShowDuration) {
        PillText(clockText(recordElapsed(record, now)), NovaType.NumberMid.copy(fontSize = 14.sp))
    }
}

private fun clockText(ms: Long): String = TimeFormat.hms(ms).removePrefix("00:")

internal fun recordElapsed(record: RecordUi, now: Long): Long {
    val capture = record.capture
    if (capture != null) return if (capture.startedAtMs > 0L) (now - capture.startedAtMs).coerceAtLeast(0L) else 0L
    val manual = record.manual
    return if (record.method == RecordMethod.MANUAL && manual != null) {
        StopwatchMath.elapsed(manual, now)
    } else {
        (now - record.osSince).coerceAtLeast(0L)
    }
}

/** Pulsing red dot used by the record indicator. Static when motion is reduced. */
@Composable
internal fun RecordDot(reduced: Boolean, size: Int = 8, paused: Boolean = false) {
    val alpha = if (paused) 0.4f else rememberBlink(!reduced, low = 0.35f, periodMs = 900)
    Box(Modifier.size(size.dp).clip(CircleShape).background(NovaColors.Danger.copy(alpha = alpha)))
}

@Composable
internal fun rememberBlink(active: Boolean, low: Float = 0.35f, periodMs: Int = 520): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "blink")
    val value by transition.animateFloat(
        initialValue = 1f,
        targetValue = low,
        animationSpec = infiniteRepeatable(tween(periodMs), RepeatMode.Reverse),
        label = "blinkValue",
    )
    return value
}

// ---------------------------------------------------------------------------------------------
// Notification peeks
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PeekPanel(peek: PeekPayload?, actions: IslandActions) {
    if (peek == null) return
    // App name, who wrote it, and a one-line preview. Privacy filtering already happened before this point.
    val primary = peek.sender.ifBlank { peek.summary }.ifBlank { "New notification" }
    Row(
        Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(peek.packageName, 30.dp, 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            PillText(peek.appLabel.uppercase(), NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp))
            PillText(primary, NovaType.BodyStrong.copy(fontSize = 14.sp, lineHeight = 18.sp))
            val body = peek.body
            if (body != null) {
                PillText(body, NovaType.Body.copy(fontSize = 12.sp, lineHeight = 15.sp), color = NovaColors.Text2)
            }
        }
        if (peek.canReply) {
            Spacer(Modifier.width(6.dp))
            RoundButton(
                icon = NovaIcon.REPLY,
                label = "Reply",
                onClick = { actions.peekReply() },
                size = 40.dp,
                container = NovaColors.Surface3,
                iconSize = 18.dp,
            )
        }
        peek.actions.take(2).forEachIndexed { index, action ->
            Spacer(Modifier.width(6.dp))
            PillButton(
                text = action.title,
                onClick = { actions.peekAction(index) },
                modifier = Modifier.widthIn(max = 78.dp),
                height = 36.dp,
                fontSize = 12,
            )
        }
    }
}

@Composable
internal fun CallPanel(peek: PeekPayload?, actions: IslandActions, reduced: Boolean) {
    if (peek == null) return
    val transition = rememberInfiniteTransition(label = "ring")
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "ringValue",
    )
    Row(
        Modifier.fillMaxSize().padding(start = 13.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .drawBehind {
                    if (!reduced) {
                        val base = size.minDimension / 2f
                        drawCircle(
                            color = NovaColors.Green.copy(alpha = (1f - ring) * 0.55f),
                            radius = base + base * 0.32f * ring,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(peek.packageName, 40.dp, 20.dp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            PillText("INCOMING CALL", NovaType.Label.copy(fontSize = 10.sp, lineHeight = 12.sp), color = NovaColors.Green)
            PillText(
                peek.summary.ifBlank { peek.appLabel },
                NovaType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp),
            )
        }
        if (peek.decline != null) {
            RoundButton(
                icon = NovaIcon.PHONE_DECLINE,
                label = "Decline call",
                onClick = { actions.peekDecline() },
                container = NovaColors.Danger,
                tint = Color.White,
                iconSize = 22.dp,
            )
        }
        if (peek.answer != null) {
            Spacer(Modifier.width(8.dp))
            RoundButton(
                icon = NovaIcon.PHONE_ACCEPT,
                label = "Answer call",
                onClick = { actions.peekAnswer() },
                container = NovaColors.Green,
                tint = Color.Black,
                iconSize = 22.dp,
            )
        }
        if (peek.decline == null && peek.answer == null) {
            NovaIconView(NovaIcon.CHEVRON_RIGHT, NovaColors.Text3, size = 20.dp)
        }
    }
}
