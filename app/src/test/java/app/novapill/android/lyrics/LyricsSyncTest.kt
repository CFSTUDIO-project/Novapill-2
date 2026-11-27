package app.novapill.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSyncTest {
    private val lines = listOf(
        LyricLine(1_000, "a"),
        LyricLine(5_000, "b"),
        LyricLine(5_000, "b2"),
        LyricLine(9_000, "c"),
    )

    @Test
    fun beforeFirstLineIsMinusOne() {
        assertEquals(-1, LyricsSync.activeIndex(lines, 0))
        assertEquals(-1, LyricsSync.activeIndex(lines, 999))
        assertEquals(-1, LyricsSync.activeIndex(emptyList(), 5_000))
    }

    @Test
    fun exactAndBetweenTimestamps() {
        assertEquals(0, LyricsSync.activeIndex(lines, 1_000))
        assertEquals(0, LyricsSync.activeIndex(lines, 4_999))
        assertEquals(3, LyricsSync.activeIndex(lines, 9_000))
        assertEquals(3, LyricsSync.activeIndex(lines, 999_999))
    }

    @Test
    fun duplicateTimestampsResolveToLastOfGroup() {
        assertEquals(2, LyricsSync.activeIndex(lines, 5_000))
        assertEquals(2, LyricsSync.activeIndex(lines, 5_001))
    }

    @Test
    fun seekBackwardsMovesIndexBackwards() {
        assertEquals(3, LyricsSync.activeIndex(lines, 9_500))
        assertEquals(0, LyricsSync.activeIndex(lines, 2_000))
    }

    @Test
    fun offsetShiftsLookupConsistently() {
        // +500 ms: lyrics appear earlier, so the line at 5_000 is active at 4_500.
        assertEquals(2, LyricsSync.activeIndex(lines, LyricsSync.lookupPosition(4_500, 500)))
        assertEquals(0, LyricsSync.activeIndex(lines, LyricsSync.lookupPosition(4_500, 0)))
        // -500 ms: lyrics appear later.
        assertEquals(0, LyricsSync.activeIndex(lines, LyricsSync.lookupPosition(5_200, -500)))
    }

    @Test
    fun wordIndexOnlyForRealWordTiming() {
        val plain = LyricLine(0, "x")
        assertEquals(-1, LyricsSync.activeWordIndex(plain, 500))
        val timed = LyricLine(0, "a b c", listOf(LyricWord(0, "a"), LyricWord(400, "b"), LyricWord(900, "c")))
        assertEquals(0, LyricsSync.activeWordIndex(timed, 100))
        assertEquals(1, LyricsSync.activeWordIndex(timed, 400))
        assertEquals(2, LyricsSync.activeWordIndex(timed, 5_000))
    }

    @Test
    fun lineProgressIsBounded() {
        assertEquals(0f, LyricsSync.lineProgress(lines, 0, 1_000), 0.001f)
        assertEquals(0.5f, LyricsSync.lineProgress(lines, 0, 3_000), 0.001f)
        assertEquals(1f, LyricsSync.lineProgress(lines, 0, 50_000), 0.001f)
        assertEquals(0f, LyricsSync.lineProgress(lines, -1, 3_000), 0.001f)
    }

    @Test
    fun clampOffsetStaysInRange() {
        assertEquals(1_500, LyricsSync.clampOffset(9_999))
        assertEquals(-1_500, LyricsSync.clampOffset(-9_999))
        assertEquals(250, LyricsSync.clampOffset(250))
    }
}
