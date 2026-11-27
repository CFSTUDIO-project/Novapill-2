package app.novapill.android.core

import app.novapill.android.settings.RecQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RecorderMachineTest {
    private val idle = RecorderUi()

    @Test
    fun aFullSuccessfulRecordingWalksThroughEveryPhase() {
        var s = RecorderMachine.requested(idle)
        assertEquals(RecPhase.PREPARING, s.phase)
        s = RecorderMachine.consentGranted(s, 3, withMic = true)
        assertEquals(3, s.countdownLeft)
        assertTrue(s.withMic)
        s = RecorderMachine.countdown(s, 1)
        assertEquals(1, s.countdownLeft)
        assertEquals(0L, s.startedAtMs)
        s = RecorderMachine.started(s, 5_000L)
        assertEquals(RecPhase.RECORDING, s.phase)
        assertEquals(5_000L, s.startedAtMs)
        assertEquals(0, s.countdownLeft)
        s = RecorderMachine.stopping(s)
        assertEquals(RecPhase.STOPPING, s.phase)
        s = RecorderMachine.saved(s, "a.mp4", "content://x/1")
        assertEquals(RecPhase.SAVED, s.phase)
        assertEquals("a.mp4", s.savedName)
        assertEquals(0L, s.startedAtMs)
        s = RecorderMachine.dismissed(s)
        assertEquals(idle, s)
    }

    @Test
    fun aTimerNeverRunsWithoutARecording() {
        var s = RecorderMachine.requested(idle)
        s = RecorderMachine.consentGranted(s, 0, false)
        assertEquals(0L, s.startedAtMs)
        s = RecorderMachine.failed(s, RecError.START_FAILED)
        assertEquals(RecPhase.ERROR, s.phase)
        assertEquals(0L, s.startedAtMs)
    }

    @Test
    fun deniedConsentIsAnError() {
        var s = RecorderMachine.requested(idle)
        s = RecorderMachine.consentDenied(s)
        assertEquals(RecPhase.ERROR, s.phase)
        assertEquals(RecError.CONSENT_DENIED, s.error)
        assertFalse(s.busy)
        assertTrue(s.visible)
    }

    @Test
    fun cancelBeforeTheEncoderStartedLeavesNothing() {
        var s = RecorderMachine.requested(idle)
        s = RecorderMachine.consentGranted(s, 5, false)
        s = RecorderMachine.stopping(s)
        assertEquals(idle, s)
    }

    @Test
    fun lateCallbacksCannotCorruptTheState() {
        assertEquals(idle, RecorderMachine.started(idle, 10))
        assertEquals(idle, RecorderMachine.stopping(idle))
        assertEquals(idle, RecorderMachine.saved(idle, "a", "b"))
        assertEquals(idle, RecorderMachine.failed(idle, RecError.EMPTY_FILE))
        assertEquals(idle, RecorderMachine.dismissed(idle))
        val rec = RecorderMachine.started(RecorderMachine.requested(idle), 1)
        assertEquals(rec, RecorderMachine.started(rec, 99))
        assertEquals(rec, RecorderMachine.requested(rec))
        assertEquals(rec, RecorderMachine.dismissed(rec))
    }

    @Test
    fun aSystemStopStillCanSave() {
        var s = RecorderMachine.started(RecorderMachine.requested(idle), 100)
        s = RecorderMachine.withNote(s, RecNote.STOPPED_BY_SYSTEM)
        s = RecorderMachine.saved(s, "a.mp4", "u")
        assertEquals(RecPhase.SAVED, s.phase)
        assertEquals(RecNote.STOPPED_BY_SYSTEM, s.note)
    }

    @Test
    fun anErrorCanBeRetried() {
        var s = RecorderMachine.failed(RecorderMachine.requested(idle), RecError.ENCODER_FAILED)
        s = RecorderMachine.requested(s)
        assertEquals(RecPhase.PREPARING, s.phase)
        assertNull(s.error)
    }

    @Test
    fun profileScalesDownKeepsAspectAndUsesEvenSizes() {
        val p = RecordingProfile.compute(1080, 2400, 420, RecQuality.P720, 30)
        assertEquals(720, p.width)
        assertEquals(1600, p.height)
        assertEquals(30, p.fps)
        assertTrue(p.bitrate in 2_000_000..24_000_000)
        val odd = RecordingProfile.compute(1081, 2401, 420, RecQuality.P720, 30)
        assertEquals(0, odd.width % 2)
        assertEquals(0, odd.height % 2)
    }

    @Test
    fun profileNeverScalesUp() {
        val p = RecordingProfile.compute(480, 800, 240, RecQuality.P1080, 30)
        assertEquals(480, p.width)
        assertEquals(800, p.height)
    }

    @Test
    fun landscapeScreensKeepTheShortSide() {
        val p = RecordingProfile.compute(2400, 1080, 420, RecQuality.P720, 60)
        assertEquals(1600, p.width)
        assertEquals(720, p.height)
        assertEquals(60, p.fps)
    }

    @Test
    fun bitrateGrowsWithQualityAndFrameRate() {
        val a = RecordingProfile.compute(1080, 2400, 420, RecQuality.P720, 30)
        val b = RecordingProfile.compute(1080, 2400, 420, RecQuality.P1080, 30)
        val c = RecordingProfile.compute(1080, 2400, 420, RecQuality.P1080, 60)
        assertTrue(b.bitrate > a.bitrate)
        assertTrue(c.bitrate > b.bitrate)
    }

    @Test
    fun fallbackOnlyWhenThereIsSomethingLower() {
        assertNull(RecordingProfile.fallback(1080, 2400, 420, RecQuality.P720, 30))
        val f = RecordingProfile.fallback(1080, 2400, 420, RecQuality.P1080, 60)
        assertEquals(720, f?.width)
        assertEquals(30, f?.fps)
    }

    @Test
    fun fileNameHasTheTimestampAndExtension() {
        val name = RecordingNames.fileName(0L, TimeZone.getTimeZone("UTC"))
        assertEquals("NOVA-PILL_19700101_000000.mp4", name)
    }
}
