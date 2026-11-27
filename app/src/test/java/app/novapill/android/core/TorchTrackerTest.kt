package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorchTrackerTest {
    @Test
    fun nothingIsOnBeforeTheSystemReports() {
        val t = TorchTracker()
        assertEquals(TorchStatus.UNKNOWN, t.status())
        t.track("0")
        t.track("2")
        assertEquals(TorchStatus.UNKNOWN, t.status())
        assertTrue(t.idsOn().isEmpty())
    }

    @Test
    fun reportsDriveTheStatus() {
        val t = TorchTracker()
        t.track("0")
        t.onChanged("0", false)
        assertEquals(TorchStatus.OFF, t.status())
        t.onChanged("0", true)
        assertEquals(TorchStatus.ON, t.status())
        assertEquals(listOf("0"), t.idsOn())
        t.onChanged("0", false)
        assertEquals(TorchStatus.OFF, t.status())
    }

    @Test
    fun unavailableClearsAStaleOn() {
        // The bug of version 1: the camera app opens, the system turns the torch off and only reports
        // "unavailable". The old code kept showing On.
        val t = TorchTracker()
        t.onChanged("0", true)
        t.onUnavailable("0")
        assertEquals(TorchStatus.BUSY, t.status())
        assertFalse(t.status() == TorchStatus.ON)
        assertTrue(t.idsOn().isEmpty())
        t.onChanged("0", false)
        assertEquals(TorchStatus.OFF, t.status())
    }

    @Test
    fun anyUnitOnMeansOn() {
        val t = TorchTracker()
        t.track("0")
        t.track("2")
        t.onChanged("0", false)
        t.onChanged("2", true)
        assertEquals(TorchStatus.ON, t.status())
        assertEquals(listOf("2"), t.idsOn())
    }

    @Test
    fun mixedOffAndBusyIsOff() {
        val t = TorchTracker()
        t.onChanged("0", false)
        t.onUnavailable("2")
        assertEquals(TorchStatus.OFF, t.status())
    }

    @Test
    fun resetForgetsEverything() {
        val t = TorchTracker()
        t.onChanged("0", true)
        t.reset()
        assertEquals(TorchStatus.UNKNOWN, t.status())
        assertTrue(t.ids.isEmpty())
    }

    @Test
    fun pickForOnPrefersAUnitThatReportedOff() {
        val t = TorchTracker()
        t.track("0")
        t.track("1")
        t.onChanged("1", false)
        assertEquals("1", t.pickForOn(listOf("0", "1")))
        t.onChanged("0", false)
        assertEquals("0", t.pickForOn(listOf("0", "1")))
    }

    @Test
    fun pickForOnFallsBackToUnknownAndSkipsBusy() {
        val t = TorchTracker()
        t.track("0")
        t.onUnavailable("1")
        assertEquals("0", t.pickForOn(listOf("1", "0")))
        t.onUnavailable("0")
        assertNull(t.pickForOn(listOf("1", "0")))
    }

    @Test
    fun confirmationNeedsTheMatchingReport() {
        assertFalse(TorchConfirm.confirmed(TorchPending.NONE, TorchStatus.ON))
        assertTrue(TorchConfirm.confirmed(TorchPending.TO_ON, TorchStatus.ON))
        assertFalse(TorchConfirm.confirmed(TorchPending.TO_ON, TorchStatus.OFF))
        assertFalse(TorchConfirm.confirmed(TorchPending.TO_ON, TorchStatus.UNKNOWN))
        assertTrue(TorchConfirm.confirmed(TorchPending.TO_OFF, TorchStatus.OFF))
        assertTrue(TorchConfirm.confirmed(TorchPending.TO_OFF, TorchStatus.BUSY))
        assertFalse(TorchConfirm.confirmed(TorchPending.TO_OFF, TorchStatus.ON))
    }

    @Test
    fun strengthLevelMapping() {
        assertEquals(1, TorchConfirm.levelFor(100, 1))
        assertEquals(5, TorchConfirm.levelFor(100, 5))
        assertEquals(1, TorchConfirm.levelFor(1, 5))
        assertEquals(3, TorchConfirm.levelFor(50, 5))
        assertEquals(5, TorchConfirm.levelFor(500, 5))
        assertEquals(1, TorchConfirm.levelFor(-9, 5))
    }
}
