package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeekQueueTest {
    private fun e(key: String, high: Boolean = false, sticky: Boolean = false, payload: String = key) =
        PeekEntry(key, high, sticky, payload)

    @Test
    fun firstOfferIsShownAndOthersQueueInOrder() {
        val q = PeekQueue<String>()
        assertEquals(OfferResult.SHOWN_NOW, q.offer(e("a")))
        assertEquals(OfferResult.QUEUED, q.offer(e("b")))
        assertEquals(OfferResult.QUEUED, q.offer(e("c")))
        assertEquals("a", q.current?.key)
        assertEquals("b", q.advance()?.key)
        assertEquals("c", q.advance()?.key)
        assertNull(q.advance())
    }

    @Test
    fun sameKeyUpdatesInPlace() {
        val q = PeekQueue<String>()
        q.offer(e("a", payload = "old"))
        q.offer(e("b"))
        assertEquals(OfferResult.UPDATED_CURRENT, q.offer(e("a", payload = "new")))
        assertEquals("new", q.current?.payload)
        assertEquals(OfferResult.UPDATED_QUEUED, q.offer(e("b", payload = "b2")))
        assertEquals(2, q.size)
    }

    @Test
    fun highPriorityPreemptsAndNormalReturnsAfter() {
        val q = PeekQueue<String>()
        q.offer(e("n1"))
        assertEquals(OfferResult.PREEMPTED, q.offer(e("call", high = true, sticky = true)))
        assertEquals("call", q.current?.key)
        assertEquals("n1", q.advance()?.key)
    }

    @Test
    fun secondHighGoesBehindFirstHighButAheadOfNormals() {
        val q = PeekQueue<String>()
        q.offer(e("n1"))
        q.offer(e("h1", high = true))
        q.offer(e("n2"))
        assertEquals(OfferResult.QUEUED, q.offer(e("h2", high = true)))
        assertEquals(listOf("h1", "h2", "n1", "n2"), q.snapshot().map { it.key })
    }

    @Test
    fun queueStaysShortAndKeepsTheVisibleEntry() {
        val q = PeekQueue<String>(maxSize = 3)
        for (i in 1..8) q.offer(e("n$i"))
        assertEquals(3, q.size)
        assertEquals("n1", q.current?.key)
    }

    @Test
    fun latestOnlyReplacesNormalsButKeepsHigh() {
        val q = PeekQueue<String>()
        q.offer(e("a"))
        q.offer(e("b"))
        q.offer(e("c"), latestOnly = true)
        assertEquals(listOf("c"), q.snapshot().map { it.key })
        q.offer(e("call", high = true))
        q.offer(e("d"), latestOnly = true)
        assertEquals(listOf("call", "d"), q.snapshot().map { it.key })
    }

    @Test
    fun removeReportsWhatHappened() {
        val q = PeekQueue<String>()
        q.offer(e("a"))
        q.offer(e("b"))
        assertEquals(RemoveResult.REMOVED, q.remove("b"))
        assertEquals(RemoveResult.NOT_FOUND, q.remove("zzz"))
        assertEquals(RemoveResult.CURRENT_REMOVED, q.remove("a"))
        assertNull(q.current)
    }
}
