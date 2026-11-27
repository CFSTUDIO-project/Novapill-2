package app.novapill.android.core

import app.novapill.android.settings.PauseBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandStateMachineTest {
    private fun run(block: StateInputs.() -> StateInputs): StateOutput =
        IslandStateMachine.resolve(StateInputs(serviceRunning = true).block())

    @Test
    fun offWhenServiceStopped() {
        val o = IslandStateMachine.resolve(StateInputs(serviceRunning = false, media = MediaPlayState.PLAYING))
        assertEquals(IslandPhase.OFF, o.phase)
        assertFalse(o.visible)
        assertTrue(o.cards.isEmpty())
    }

    @Test
    fun idlePillShowsThenDisappearsAfterTimeout() {
        val fresh = run { copy(idleForMs = 0, idleTimeoutMs = 6_000) }
        assertEquals(IslandPhase.IDLE, fresh.phase)
        assertTrue(fresh.visible)
        assertEquals(6_000L, fresh.recheckInMs)
        val later = run { copy(idleForMs = 6_000, idleTimeoutMs = 6_000) }
        assertFalse(later.visible)
        assertNull(later.recheckInMs)
    }

    @Test
    fun minimalIdlePillStaysVisible() {
        val o = run { copy(idleForMs = 999_999, showMinimalIdlePill = true) }
        assertTrue(o.visible)
        assertEquals(IslandPhase.IDLE, o.phase)
        assertNull(o.recheckInMs)
    }

    @Test
    fun playingBufferingAndPausedPhases() {
        assertEquals(IslandPhase.MEDIA_PLAYING, run { copy(media = MediaPlayState.PLAYING) }.phase)
        assertEquals(IslandPhase.MEDIA_BUFFERING, run { copy(media = MediaPlayState.BUFFERING) }.phase)
        val paused = run { copy(media = MediaPlayState.PAUSED, mediaInactiveForMs = 1_000) }
        assertEquals(IslandPhase.MEDIA_PAUSED, paused.phase)
        assertEquals(CardKind.MEDIA, paused.primary)
        assertTrue(paused.visible)
    }

    @Test
    fun pausedMediaKeepsMetadataDuringGraceAndReportsExpiry() {
        val inGrace = run { copy(media = MediaPlayState.PAUSED, mediaInactiveForMs = 3_000, pausedGraceMs = 8_000) }
        assertFalse(inGrace.graceExpired)
        assertEquals(5_000L, inGrace.recheckInMs)
        val expired = run { copy(media = MediaPlayState.PAUSED, mediaInactiveForMs = 9_000, pausedGraceMs = 8_000) }
        assertTrue(expired.graceExpired)
        assertTrue(expired.visible)
    }

    @Test
    fun hideBehaviorRemovesPausedCardAfterGrace() {
        val o = run {
            copy(media = MediaPlayState.PAUSED, mediaInactiveForMs = 9_000, pausedGraceMs = 8_000,
                pauseBehavior = PauseBehavior.HIDE, idleForMs = 100_000)
        }
        assertFalse(o.visible)
        assertTrue(CardKind.MEDIA !in o.cards)
    }

    @Test
    fun keepVisibleNeverSchedulesGraceRecheck() {
        val o = run { copy(media = MediaPlayState.PAUSED, mediaInactiveForMs = 1_000, pauseBehavior = PauseBehavior.KEEP_VISIBLE) }
        assertNull(o.recheckInMs)
    }

    @Test
    fun stoppedMediaHiddenWhenConfigured() {
        val hidden = run { copy(media = MediaPlayState.STOPPED, hideAfterMediaStops = true, idleForMs = 100_000) }
        assertFalse(hidden.visible)
        val kept = run { copy(media = MediaPlayState.STOPPED, hideAfterMediaStops = false) }
        assertEquals(CardKind.MEDIA, kept.primary)
    }

    @Test
    fun peekLayersOverMediaWithoutLosingIt() {
        val o = run { copy(media = MediaPlayState.PLAYING, peekActive = true) }
        assertEquals(IslandPhase.NOTIFICATION_PEEK, o.phase)
        assertEquals(CardKind.MEDIA, o.primary)
        assertTrue(o.visible)
        val back = run { copy(media = MediaPlayState.PLAYING, peekActive = false) }
        assertEquals(IslandPhase.MEDIA_PLAYING, back.phase)
    }

    @Test
    fun peekShowsEvenWhenIdleExpired() {
        val o = run { copy(peekActive = true, idleForMs = 100_000) }
        assertTrue(o.visible)
        assertEquals(IslandPhase.NOTIFICATION_PEEK, o.phase)
    }

    @Test
    fun recordingWithMusicBecomesBadge() {
        val o = run { copy(media = MediaPlayState.PLAYING, recordingActive = true) }
        assertEquals(CardKind.MEDIA, o.primary)
        assertEquals(listOf(CardKind.RECORD), o.badges)
        val alone = run { copy(recordingActive = true) }
        assertEquals(IslandPhase.RECORDING_INDICATOR, alone.phase)
        assertTrue(alone.badges.isEmpty())
    }

    @Test
    fun timerPhasesAndPriority() {
        val running = run { copy(media = MediaPlayState.PLAYING, timer = TimerPhase.RUNNING) }
        assertEquals(CardKind.MEDIA, running.primary)
        assertEquals(listOf(CardKind.TIMER), running.badges)
        val finished = run { copy(media = MediaPlayState.PLAYING, timer = TimerPhase.FINISHED) }
        assertEquals(CardKind.TIMER, finished.primary)
        assertEquals(IslandPhase.TIMER_FINISHED, finished.phase)
        assertEquals(IslandPhase.TIMER_PAUSED, run { copy(timer = TimerPhase.PAUSED) }.phase)
        assertEquals(IslandPhase.TIMER_RUNNING, run { copy(timer = TimerPhase.RUNNING) }.phase)
    }

    @Test
    fun utilityCards() {
        assertEquals(IslandPhase.UTILITY_ACTIVE, run { copy(stopwatch = StopwatchPhase.RUNNING) }.phase)
        assertEquals(CardKind.STOPWATCH, run { copy(stopwatch = StopwatchPhase.PAUSED) }.primary)
        val flash = run { copy(flashOn = true) }
        assertEquals(CardKind.TOOLS, flash.primary)
        assertEquals(IslandPhase.UTILITY_ACTIVE, flash.phase)
        assertTrue(run { copy(toolsRequested = true, idleForMs = 100_000) }.visible)
    }

    @Test
    fun swipeOrderIsStableWithToolsLast() {
        val o = run {
            copy(media = MediaPlayState.PLAYING, timer = TimerPhase.RUNNING, recordingActive = true, stopwatch = StopwatchPhase.RUNNING)
        }
        assertEquals(
            listOf(CardKind.MEDIA, CardKind.TIMER, CardKind.RECORD, CardKind.STOPWATCH, CardKind.TOOLS),
            o.cards,
        )
    }

    @Test
    fun toolsOnlyReachableWhenSomethingIsShown() {
        val gone = run { copy(idleForMs = 100_000) }
        assertTrue(gone.cards.isEmpty())
    }
}
