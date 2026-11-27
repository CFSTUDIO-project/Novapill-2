package app.novapill.android.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeekTimerTest {
    private fun entry(key: String, sticky: Boolean = false, high: Boolean = false) = PeekEntry(key, high, sticky, key)

    @Test
    fun entryDisappearsExactlyAfterConfiguredDuration() = runTest {
        for (seconds in listOf(2L, 3L, 4L, 6L)) {
            val shown = mutableListOf<String?>()
            val timer = PeekTimer(backgroundScope, PeekQueue<String>(), { seconds * 1000 }) { shown.add(it?.key) }
            timer.offer(entry("a$seconds"), false)
            runCurrent()
            advanceTimeBy(seconds * 1000 - 1)
            runCurrent()
            assertEquals("still visible just before $seconds s", "a$seconds", timer.current?.key)
            advanceTimeBy(2)
            runCurrent()
            assertNull("gone after $seconds s", timer.current)
            assertEquals(listOf<String?>("a$seconds", null), shown)
        }
    }

    @Test
    fun queuedEntriesShowOneAfterAnother() = runTest {
        val shown = mutableListOf<String?>()
        val timer = PeekTimer(backgroundScope, PeekQueue<String>(), { 2_000L }) { shown.add(it?.key) }
        timer.offer(entry("a"), false)
        timer.offer(entry("b"), false)
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals("b", timer.current?.key)
        advanceTimeBy(2_001)
        runCurrent()
        assertNull(timer.current)
        assertEquals(listOf<String?>("a", "b", null), shown)
    }

    @Test
    fun updatingTheVisibleEntryRestartsTheTimer() = runTest {
        val timer = PeekTimer(backgroundScope, PeekQueue<String>(), { 2_000L }) { }
        timer.offer(entry("a"), false)
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()
        timer.offer(entry("a"), false)
        runCurrent()
        advanceTimeBy(1_500)
        runCurrent()
        assertEquals("a", timer.current?.key)
        advanceTimeBy(600)
        runCurrent()
        assertNull(timer.current)
    }

    @Test
    fun stickyEntryNeverTimesOutUntilRemoved() = runTest {
        val timer = PeekTimer(backgroundScope, PeekQueue<String>(), { 2_000L }) { }
        timer.offer(entry("call", sticky = true, high = true), false)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("call", timer.current?.key)
        timer.remove("call")
        assertNull(timer.current)
    }

    @Test
    fun holdingPausesAndReleaseRestartsFullDuration() = runTest {
        val timer = PeekTimer(backgroundScope, PeekQueue<String>(), { 2_000L }) { }
        timer.offer(entry("a"), false)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        timer.hold(true)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals("a", timer.current?.key)
        timer.hold(false)
        runCurrent()
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals("a", timer.current?.key)
        advanceTimeBy(2)
        runCurrent()
        assertNull(timer.current)
    }
}
