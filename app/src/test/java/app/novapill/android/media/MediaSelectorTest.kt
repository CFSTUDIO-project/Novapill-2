package app.novapill.android.media

import app.novapill.android.core.MediaPlayState
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSelectorTest {
    private fun c(pkg: String, state: MediaPlayState, recency: Long = 0, meta: Boolean = true) =
        SessionCandidate(pkg, state, meta, recency)

    @Test
    fun playingBeatsPausedEvenForPriorityPlayers() {
        val list = listOf(c("com.spotify.music", MediaPlayState.PAUSED), c("org.other", MediaPlayState.PLAYING))
        assertEquals(1, MediaSelector.select(list, ""))
    }

    @Test
    fun spotifyWinsATieBetweenPlayers() {
        val list = listOf(c("org.other", MediaPlayState.PLAYING, recency = 99), c("com.spotify.music", MediaPlayState.PLAYING))
        assertEquals(1, MediaSelector.select(list, ""))
    }

    @Test
    fun recencyBreaksTieBetweenUnknownPlayers() {
        val list = listOf(c("a", MediaPlayState.PAUSED, 10), c("b", MediaPlayState.PAUSED, 50))
        assertEquals(1, MediaSelector.select(list, ""))
    }

    @Test
    fun preferredPlayerIsStrict() {
        val list = listOf(c("com.spotify.music", MediaPlayState.PLAYING), c("org.other", MediaPlayState.PAUSED))
        assertEquals(1, MediaSelector.select(list, "org.other"))
        assertEquals(-1, MediaSelector.select(list, "org.missing"))
    }

    @Test
    fun emptySessionsAreIgnored() {
        assertEquals(-1, MediaSelector.select(emptyList(), ""))
        assertEquals(-1, MediaSelector.select(listOf(c("a", MediaPlayState.NONE, meta = false)), ""))
        assertEquals(0, MediaSelector.select(listOf(c("a", MediaPlayState.NONE, meta = true)), ""))
    }
}
