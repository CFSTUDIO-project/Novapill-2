package app.novapill.android.media

import app.novapill.android.core.MediaPlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackInfoTest {
    private fun info(state: MediaPlayState, pos: Long = 10_000, speed: Float = 1f, at: Long = 1_000) =
        PlaybackInfo(state, pos, speed, at, 0)

    @Test
    fun playingAdvancesWithSpeedOnTheMonotonicClock() {
        assertEquals(12_000L, info(MediaPlayState.PLAYING).positionAt(3_000, 200_000))
        assertEquals(10_000L + 4_000L, info(MediaPlayState.PLAYING, speed = 2f).positionAt(3_000, 200_000))
    }

    @Test
    fun pausedAndBufferingDoNotAdvance() {
        assertEquals(10_000L, info(MediaPlayState.PAUSED).positionAt(99_000, 200_000))
        assertEquals(10_000L, info(MediaPlayState.BUFFERING).positionAt(99_000, 200_000))
    }

    @Test
    fun positionIsClampedToDuration() {
        assertEquals(15_000L, info(MediaPlayState.PLAYING).positionAt(60_000, 15_000))
        assertEquals(0L, info(MediaPlayState.PLAYING, pos = -50).positionAt(1_000, 15_000))
    }

    @Test
    fun clockGoingBackwardsNeverRewinds() {
        assertEquals(10_000L, info(MediaPlayState.PLAYING).positionAt(500, 200_000))
    }

    @Test
    fun stateCodesMapToIslandStates() {
        assertEquals(MediaPlayState.PLAYING, PlaybackStateMapper.fromCode(3))
        assertEquals(MediaPlayState.PAUSED, PlaybackStateMapper.fromCode(2))
        assertEquals(MediaPlayState.BUFFERING, PlaybackStateMapper.fromCode(6))
        assertEquals(MediaPlayState.BUFFERING, PlaybackStateMapper.fromCode(10))
        assertEquals(MediaPlayState.STOPPED, PlaybackStateMapper.fromCode(1))
        assertEquals(MediaPlayState.STOPPED, PlaybackStateMapper.fromCode(7))
        assertEquals(MediaPlayState.NONE, PlaybackStateMapper.fromCode(0))
        assertEquals(MediaPlayState.NONE, PlaybackStateMapper.fromCode(99))
    }

    @Test
    fun transportActionsAreOnlyEnabledWhenAdvertised() {
        val none = 0L
        assertTrue(!TransportActions.canPause(none) && !TransportActions.canNext(none) && !TransportActions.canSeek(none))
        val some = TransportActions.PLAY_PAUSE or TransportActions.SKIP_TO_NEXT
        assertTrue(TransportActions.canPlay(some) && TransportActions.canPause(some) && TransportActions.canNext(some))
        assertTrue(!TransportActions.canPrevious(some) && !TransportActions.canSeek(some))
    }
}
