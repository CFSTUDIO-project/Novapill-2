package app.novapill.android.notif

import app.novapill.android.settings.PreviewPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    private fun lines(
        privacy: PreviewPrivacy = PreviewPrivacy.FULL,
        sender: String? = "Dina",
        title: String? = "Dina",
        body: String? = "Jadi jam berapa ketemuannya?",
        locked: Boolean = false,
        secret: Boolean = false,
    ) = NotificationText.lines("WhatsApp", sender, title, body, privacy, locked, secret)

    @Test
    fun fullShowsSenderAndBody() {
        val l = lines()
        assertEquals("WhatsApp", l.app)
        assertEquals("Dina", l.sender)
        assertEquals("Jadi jam berapa ketemuannya?", l.body)
    }

    @Test
    fun summaryHidesTheBody() {
        val l = lines(privacy = PreviewPrivacy.SUMMARY)
        assertEquals("Dina", l.sender)
        assertNull(l.body)
    }

    @Test
    fun appOnlyHidesEverythingButTheAppName() {
        val l = lines(privacy = PreviewPrivacy.APP_ONLY)
        assertEquals("WhatsApp", l.app)
        assertEquals(NotificationText.GENERIC, l.sender)
        assertNull(l.body)
    }

    @Test
    fun lockedOrSecretNeverShowsContent() {
        assertNull(lines(locked = true).body)
        assertEquals(NotificationText.GENERIC, lines(secret = true).sender)
    }

    @Test
    fun titleStandsInWhenThereIsNoSender() {
        val l = lines(sender = null, title = "Kantor", body = "Rapat dipindah")
        assertEquals("Kantor", l.sender)
        assertEquals("Rapat dipindah", l.body)
    }

    @Test
    fun bodyEqualToTheSenderIsDropped() {
        val l = lines(sender = "Ibu", title = "Ibu", body = "Ibu")
        assertNull(l.body)
    }

    @Test
    fun hostileWhitespaceAndDirectionMarksAreCleaned() {
        val raw = "‮Halo‏\n\n   dunia\t\u0007!"
        assertEquals("Halo dunia !", NotificationText.clean(raw))
        assertEquals("", NotificationText.clean("   ‎  "))
        assertEquals("", NotificationText.clean(null))
    }

    @Test
    fun longTextIsCappedWithAnEllipsis() {
        val long = "a".repeat(500)
        val capped = NotificationText.cap(long, NotificationText.BODY_MAX)
        assertEquals(NotificationText.BODY_MAX, capped.length)
        assertTrue(capped.endsWith("…"))
        assertEquals("abc", NotificationText.cap("abc", 10))
    }

    @Test
    fun capNeverSplitsASurrogatePair() {
        val s = "a".repeat(9) + "😀" + "bbb"
        val capped = NotificationText.cap(s, 11)
        assertFalse(Character.isHighSurrogate(capped[capped.length - 2]))
        assertTrue(capped.endsWith("…"))
    }

    @Test
    fun dedupReportsOnlyChanges() {
        val d = NotificationDedup(capacity = 8)
        val f1 = NotificationDedup.fingerprint("pkg", "Dina", "halo")
        val f2 = NotificationDedup.fingerprint("pkg", "Dina", "halo!")
        assertTrue(d.isNew("k", f1))
        assertFalse(d.isNew("k", f1))
        assertTrue(d.isNew("k", f2))
        d.forget("k")
        assertTrue(d.isNew("k", f2))
    }

    @Test
    fun dedupIsBoundedAndForgetsTheOldest() {
        val d = NotificationDedup(capacity = 3)
        for (i in 0 until 4) d.isNew("k$i", i.toLong())
        assertEquals(3, d.size())
        assertTrue(d.isNew("k0", 0L))
    }

    @Test
    fun fingerprintSeparatesFieldBoundaries() {
        val a = NotificationDedup.fingerprint("ab", "c")
        val b = NotificationDedup.fingerprint("a", "bc")
        assertTrue(a != b)
        assertEquals(NotificationDedup.fingerprint("x", null), NotificationDedup.fingerprint("x", ""))
    }
}
