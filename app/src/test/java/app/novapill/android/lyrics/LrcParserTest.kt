package app.novapill.android.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesCentisecondTimestamps() {
        val r = LrcParser.parse("[00:12.34]Hello\n[00:15.00]World")
        assertEquals(2, r.lines.size)
        assertEquals(12_340L, r.lines[0].timeMs)
        assertEquals("Hello", r.lines[0].text)
        assertEquals(15_000L, r.lines[1].timeMs)
        assertEquals(SyncKind.LINE, r.syncKind)
        assertEquals(0, r.skippedLines)
    }

    @Test
    fun fractionDigitsAreScaledNotTruncated() {
        val r = LrcParser.parse("[01:02.5]a\n[01:02.050]b\n[1:02]c\n[00:01.05]d")
        assertEquals(listOf(1_050L, 62_000L, 62_050L, 62_500L), r.lines.map { it.timeMs })
    }

    @Test
    fun multipleTimestampsOnOneLineExpandAndSort() {
        val r = LrcParser.parse("[00:20.00][00:10.00]Chorus\n[00:15.00]Verse")
        assertEquals(listOf("Chorus", "Verse", "Chorus"), r.lines.map { it.text })
        assertEquals(listOf(10_000L, 15_000L, 20_000L), r.lines.map { it.timeMs })
    }

    @Test
    fun malformedLinesAreSkippedAndCounted() {
        val r = LrcParser.parse("[00:75.00]bad seconds\n[xx:12.00]bad2\nplain text\n[00:05.00]ok")
        assertEquals(1, r.lines.size)
        assertEquals("ok", r.lines[0].text)
        assertEquals(3, r.skippedLines)
    }

    @Test
    fun readsMetadataAndAppliesPositiveOffsetAsEarlier() {
        val r = LrcParser.parse("[ti:Song]\n[ar:Artist]\n[al:Album]\n[offset:+500]\n[00:10.00]Line")
        assertEquals("Song", r.title)
        assertEquals("Artist", r.artist)
        assertEquals("Album", r.album)
        assertEquals(500L, r.appliedOffsetMs)
        assertEquals(9_500L, r.lines.single().timeMs)
        assertEquals(10_500L, LrcParser.parse("[offset:-500]\n[00:10.00]Line").lines.single().timeMs)
    }

    @Test
    fun offsetNeverProducesNegativeTime() {
        assertEquals(0L, LrcParser.parse("[offset:5000]\n[00:01.00]x").lines.single().timeMs)
    }

    @Test
    fun blankTimedLinesAreKeptAsGaps() {
        val r = LrcParser.parse("[00:05.00]\n[00:07.00]Hi")
        assertEquals(2, r.lines.size)
        assertEquals("", r.lines[0].text)
        assertFalse(r.isEmpty)
    }

    @Test
    fun handlesBomAndCrLf() {
        val r = LrcParser.parse("﻿[00:01.00]a\r\n[00:02.00]b\r\n")
        assertEquals(listOf("a", "b"), r.lines.map { it.text })
    }

    @Test
    fun emptyOrUntimedInputHasNoLines() {
        assertEquals(SyncKind.NONE, LrcParser.parse("").syncKind)
        val r = LrcParser.parse("just words\nmore words")
        assertTrue(r.isEmpty)
        assertEquals(2, r.skippedLines)
    }

    @Test
    fun sortsOutOfOrderLinesStably() {
        val r = LrcParser.parse("[00:09.00]late\n[00:01.00]first\n[00:01.00]second")
        assertEquals(listOf("first", "second", "late"), r.lines.map { it.text })
    }

    @Test
    fun enhancedWordTagsGiveRealWordSync() {
        val r = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>big <00:11.00>world")
        val line = r.lines.single()
        assertEquals("Hello big world", line.text)
        assertNotNull(line.words)
        val words = line.words!!
        assertEquals(listOf(10_000L, 10_500L, 11_000L), words.map { it.startMs })
        assertEquals(SyncKind.WORD, r.syncKind)
    }

    @Test
    fun outOfOrderWordTagsFallBackToLineSync() {
        val r = LrcParser.parse("[00:10.00]<00:11.00>Hello <00:10.50>world")
        assertNull(r.lines.single().words)
        assertEquals(SyncKind.LINE, r.syncKind)
        assertEquals("Hello world", r.lines.single().text)
    }

    @Test
    fun lineSyncNeverPretendsToBeWordSync() {
        val r = LrcParser.parse("[00:01.00]one\n[00:02.00]two")
        assertTrue(r.lines.all { it.words == null })
        assertEquals(SyncKind.LINE, r.syncKind)
    }

    @Test
    fun hugeInputIsBounded() {
        val sb = StringBuilder()
        for (i in 0 until 9_000) sb.append("[00:").append("%02d".format(i % 60)).append(".00]x\n")
        val r = LrcParser.parse(sb.toString())
        assertTrue(r.lines.size <= LrcParser.MAX_LINES)
    }
}
