package app.novapill.android.core

/**
 * Countdown timer arithmetic on a monotonic clock (SystemClock.elapsedRealtime in production).
 * Pure functions: the caller passes `now`, so pause/resume and finish detection are unit-testable.
 */
data class CountdownState(
    val phase: TimerPhase = TimerPhase.NONE,
    val totalMs: Long = 0,
    /** Monotonic instant the countdown reaches zero while RUNNING. */
    val endsAtMs: Long = 0,
    /** Remaining time frozen while PAUSED. */
    val pausedRemainingMs: Long = 0,
)

object CountdownMath {
    fun start(totalMs: Long, now: Long): CountdownState {
        val t = totalMs.coerceAtLeast(1_000L)
        return CountdownState(TimerPhase.RUNNING, t, now + t, t)
    }

    fun remaining(s: CountdownState, now: Long): Long = when (s.phase) {
        TimerPhase.RUNNING -> (s.endsAtMs - now).coerceIn(0, s.totalMs)
        TimerPhase.PAUSED -> s.pausedRemainingMs
        TimerPhase.FINISHED -> 0
        TimerPhase.NONE -> 0
    }

    fun pause(s: CountdownState, now: Long): CountdownState =
        if (s.phase == TimerPhase.RUNNING) s.copy(phase = TimerPhase.PAUSED, pausedRemainingMs = remaining(s, now)) else s

    fun resume(s: CountdownState, now: Long): CountdownState =
        if (s.phase == TimerPhase.PAUSED) s.copy(phase = TimerPhase.RUNNING, endsAtMs = now + s.pausedRemainingMs) else s

    /** Moves RUNNING to FINISHED once the end instant has passed. */
    fun tick(s: CountdownState, now: Long): CountdownState =
        if (s.phase == TimerPhase.RUNNING && now >= s.endsAtMs) s.copy(phase = TimerPhase.FINISHED, pausedRemainingMs = 0) else s

    fun cancel(): CountdownState = CountdownState()

    /** 1.0 at the start, 0.0 at zero. */
    fun progress(s: CountdownState, now: Long): Float {
        if (s.totalMs <= 0) return 0f
        return (remaining(s, now).toFloat() / s.totalMs.toFloat()).coerceIn(0f, 1f)
    }

    fun addTime(s: CountdownState, extraMs: Long, now: Long): CountdownState = when (s.phase) {
        TimerPhase.RUNNING -> s.copy(totalMs = s.totalMs + extraMs, endsAtMs = s.endsAtMs + extraMs)
        TimerPhase.PAUSED -> s.copy(totalMs = s.totalMs + extraMs, pausedRemainingMs = s.pausedRemainingMs + extraMs)
        TimerPhase.FINISHED -> start(extraMs, now)
        TimerPhase.NONE -> s
    }
}

data class StopwatchState(
    val phase: StopwatchPhase = StopwatchPhase.NONE,
    /** Monotonic instant of the last start/resume (valid while RUNNING). */
    val baseMs: Long = 0,
    /** Time accumulated before the last start/resume. */
    val accumulatedMs: Long = 0,
)

object StopwatchMath {
    fun start(now: Long): StopwatchState = StopwatchState(StopwatchPhase.RUNNING, now, 0)

    fun elapsed(s: StopwatchState, now: Long): Long = when (s.phase) {
        StopwatchPhase.RUNNING -> s.accumulatedMs + (now - s.baseMs).coerceAtLeast(0)
        StopwatchPhase.PAUSED -> s.accumulatedMs
        StopwatchPhase.NONE -> 0
    }

    fun pause(s: StopwatchState, now: Long): StopwatchState =
        if (s.phase == StopwatchPhase.RUNNING) StopwatchState(StopwatchPhase.PAUSED, 0, elapsed(s, now)) else s

    fun resume(s: StopwatchState, now: Long): StopwatchState =
        if (s.phase == StopwatchPhase.PAUSED) StopwatchState(StopwatchPhase.RUNNING, now, s.accumulatedMs) else s

    fun reset(): StopwatchState = StopwatchState()
}

/** Manual recording indicator timer (same arithmetic as the stopwatch, separate meaning). */
typealias RecordClockState = StopwatchState
