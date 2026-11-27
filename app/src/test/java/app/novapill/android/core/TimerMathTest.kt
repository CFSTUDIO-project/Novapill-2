package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerMathTest {
    @Test
    fun countdownRunsPausesAndResumesAccurately() {
        var s = CountdownMath.start(60_000, now = 1_000)
        assertEquals(60_000L, CountdownMath.remaining(s, 1_000))
        assertEquals(30_000L, CountdownMath.remaining(s, 31_000))
        s = CountdownMath.pause(s, 31_000)
        assertEquals(TimerPhase.PAUSED, s.phase)
        assertEquals(30_000L, CountdownMath.remaining(s, 500_000))
        s = CountdownMath.resume(s, 50_000)
        assertEquals(30_000L, CountdownMath.remaining(s, 50_000))
        assertEquals(TimerPhase.RUNNING, CountdownMath.tick(s, 79_999).phase)
        assertEquals(TimerPhase.FINISHED, CountdownMath.tick(s, 80_000).phase)
        assertEquals(0L, CountdownMath.remaining(CountdownMath.tick(s, 90_000), 90_000))
    }

    @Test
    fun progressGoesFromOneToZero() {
        val s = CountdownMath.start(10_000, 0)
        assertEquals(1f, CountdownMath.progress(s, 0), 0.001f)
        assertEquals(0.5f, CountdownMath.progress(s, 5_000), 0.001f)
        assertEquals(0f, CountdownMath.progress(s, 20_000), 0.001f)
        assertEquals(0f, CountdownMath.progress(CountdownState(), 0), 0.001f)
    }

    @Test
    fun cancelAndAddTime() {
        val s = CountdownMath.start(10_000, 0)
        val more = CountdownMath.addTime(s, 60_000, 4_000)
        assertEquals(66_000L, CountdownMath.remaining(more, 4_000))
        assertEquals(TimerPhase.NONE, CountdownMath.cancel().phase)
        val finished = CountdownMath.tick(s, 10_000)
        assertEquals(TimerPhase.RUNNING, CountdownMath.addTime(finished, 30_000, 10_000).phase)
    }

    @Test
    fun invalidStartIsClampedToOneSecond() {
        assertEquals(1_000L, CountdownMath.start(0, 0).totalMs)
    }

    @Test
    fun stopwatchAccumulatesAcrossPauses() {
        var s = StopwatchMath.start(100)
        assertEquals(500L, StopwatchMath.elapsed(s, 600))
        s = StopwatchMath.pause(s, 600)
        assertEquals(500L, StopwatchMath.elapsed(s, 10_000))
        s = StopwatchMath.resume(s, 1_000)
        assertEquals(800L, StopwatchMath.elapsed(s, 1_300))
        assertEquals(StopwatchPhase.NONE, StopwatchMath.reset().phase)
        assertEquals(0L, StopwatchMath.elapsed(StopwatchMath.reset(), 5_000))
    }

    @Test
    fun formattingIsPredictable() {
        assertEquals("0:00", TimeFormat.trackTime(0))
        assertEquals("3:07", TimeFormat.trackTime(187_000))
        assertEquals("1:02:03", TimeFormat.trackTime(3_723_000))
        assertEquals("0:00", TimeFormat.trackTime(-5))
        assertEquals("00:01", TimeFormat.countdown(1))
        assertEquals("00:00", TimeFormat.countdown(0))
        assertEquals("01:00", TimeFormat.countdown(60_000))
        assertEquals("1:01:01", TimeFormat.countdown(3_661_000))
        assertEquals("01:02:03", TimeFormat.hms(3_723_000))
        assertEquals("00:00:00", TimeFormat.hms(-1))
        assertEquals("00:12.3", TimeFormat.stopwatch(12_345))
        assertEquals("1:00:05.0", TimeFormat.stopwatch(3_605_000))
        assertEquals("00:05", TimeFormat.clock(0, 5, true))
        assertEquals("12:05", TimeFormat.clock(0, 5, false))
        assertEquals("1:07", TimeFormat.clock(13, 7, false))
        assertEquals("PM", TimeFormat.meridiem(13))
        assertEquals("AM", TimeFormat.meridiem(0))
        assertEquals("5 min", TimeFormat.presetLabel(5))
        assertEquals("1 h", TimeFormat.presetLabel(60))
        assertEquals("1 h 30 min", TimeFormat.presetLabel(90))
    }
}
