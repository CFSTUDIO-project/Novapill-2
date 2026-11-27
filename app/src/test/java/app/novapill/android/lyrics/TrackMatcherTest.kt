package app.novapill.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {
    private fun cand(
        id: String, title: String, artist: String, durMs: Long,
        synced: Boolean = true, plain: Boolean = true, instrumental: Boolean = false, fp: String? = null,
    ) = TrackMatcher.Candidate(id, title, artist, "", durMs, synced, plain, instrumental, fp)

    private val q = TrackMatcher.Query("Halo", "Beyoncé", "I Am... Sasha Fierce", 261_000)

    @Test
    fun strictFoldsCaseAccentsAndPunctuation() {
        assertEquals("don t stop me now", TrackMatcher.strict("Don't Stop Me Now!"))
        assertEquals("beyonce", TrackMatcher.strict("Beyoncé"))
        assertEquals("simon and garfunkel", TrackMatcher.strict("Simon & Garfunkel"))
    }

    @Test
    fun looseDropsCreditsAndRemasterButKeepsIdentity() {
        assertEquals("song title", TrackMatcher.loose("Song Title (feat. Someone)"))
        assertEquals("song", TrackMatcher.loose("Song - 2011 Remaster"))
        assertEquals("song", TrackMatcher.loose("Song (Remastered 2009)"))
        assertEquals("song live", TrackMatcher.loose("Song (Live)"))
        assertEquals("song remix", TrackMatcher.loose("Song (Remix)"))
        assertEquals("song", TrackMatcher.loose("Song feat. Other Artist"))
    }

    @Test
    fun scoresReflectIdentity() {
        assertEquals(1.0, TrackMatcher.titleScore("Halo", "halo"), 0.0)
        assertEquals(0.9, TrackMatcher.titleScore("Halo (feat. X)", "Halo"), 0.0)
        assertEquals(0.0, TrackMatcher.titleScore("Halo", "Single Ladies"), 0.0)
        assertEquals(1.0, TrackMatcher.artistScore("Daft Punk", "Daft Punk"), 0.0)
        assertEquals(0.85, TrackMatcher.artistScore("Artist A", "Artist A feat. B"), 0.0)
        assertEquals(0.0, TrackMatcher.artistScore("Artist A", "Someone Else"), 0.0)
    }

    @Test
    fun keysAreStableAndDurationBucketed() {
        assertEquals("daft punk|one more time", TrackMatcher.trackKey("Daft Punk", "One More Time"))
        assertEquals("daft punk|one more time|320", TrackMatcher.cacheKey("Daft Punk", "One More Time", 320_500))
        assertEquals("daft punk|one more time|0", TrackMatcher.cacheKey("Daft Punk", "One More Time", -1))
    }

    @Test
    fun singleStrongMatchIsChosen() {
        val sel = TrackMatcher.select(q, listOf(cand("1", "Halo", "Beyoncé", 261_500), cand("2", "Other", "Someone", 261_000)))
        assertTrue(sel is TrackMatcher.Selection.Single)
        assertEquals("1", (sel as TrackMatcher.Selection.Single).chosen.candidate.id)
        assertTrue(sel.chosen.strong)
    }

    @Test
    fun durationFarOffMeansNoMatch() {
        val sel = TrackMatcher.select(q, listOf(cand("1", "Halo", "Beyoncé", 300_000)))
        assertEquals(TrackMatcher.Selection.None, sel)
    }

    @Test
    fun candidatesWithoutLyricsAreIgnored() {
        val sel = TrackMatcher.select(q, listOf(cand("1", "Halo", "Beyoncé", 261_000, synced = false, plain = false)))
        assertEquals(TrackMatcher.Selection.None, sel)
    }

    @Test
    fun differentStrongCandidatesAskTheUser() {
        val sel = TrackMatcher.select(
            q,
            listOf(cand("1", "Halo", "Beyoncé", 261_000, fp = "aaa"), cand("2", "Halo", "Beyoncé", 260_500, fp = "bbb")),
        )
        assertTrue(sel is TrackMatcher.Selection.Ambiguous)
        assertEquals(2, (sel as TrackMatcher.Selection.Ambiguous).options.size)
    }

    @Test
    fun identicalLyricsAcrossDuplicatesAreNotAmbiguous() {
        val sel = TrackMatcher.select(
            q,
            listOf(cand("1", "Halo", "Beyoncé", 262_000, fp = "same"), cand("2", "Halo", "Beyoncé", 261_100, fp = "same")),
        )
        assertTrue(sel is TrackMatcher.Selection.Single)
        assertEquals("2", (sel as TrackMatcher.Selection.Single).chosen.candidate.id)
    }

    @Test
    fun weakOnlyMatchesAreNeverAutoPicked() {
        val query = TrackMatcher.Query("Love Me Like You Do", "Ellie Goulding", "", 252_000)
        val sel = TrackMatcher.select(query, listOf(cand("1", "Love Me Like You Do (Acoustic)", "Ellie Goulding", 252_000)))
        assertTrue(sel is TrackMatcher.Selection.Ambiguous)
    }

    @Test
    fun unknownQueryDurationIsNeverStrong() {
        val query = TrackMatcher.Query("Halo", "Beyoncé", "", 0)
        val sel = TrackMatcher.select(query, listOf(cand("1", "Halo", "Beyoncé", 261_000)))
        assertTrue(sel is TrackMatcher.Selection.Ambiguous)
    }

    @Test
    fun fingerprintIgnoresTimestamps() {
        val a = TrackMatcher.fingerprint(listOf(LyricLine(1, "Hello, World!"), LyricLine(2, "again")))
        val b = TrackMatcher.fingerprint(listOf(LyricLine(900, "hello world"), LyricLine(1200, "Again")))
        assertEquals(a, b)
    }
}
