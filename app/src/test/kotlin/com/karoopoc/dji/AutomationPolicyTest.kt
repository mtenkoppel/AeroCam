package com.karoopoc.dji

import org.junit.Assert.*
import org.junit.Test

class AutomationPolicyTest {
    @Test fun fullQuietMinuteAndOneStopOnly() {
        val p = AutoStopPolicy()
        assertFalse(p.update(0, 1, true, false, 60))
        assertFalse(p.update(59999, 1, true, false, 60))
        assertTrue(p.update(60000, 1, true, false, 60))
        assertFalse(p.update(61000, 1, true, false, 60))
    }
    @Test fun reboundOrOverlappingTriggerRequiresFreshMinute() {
        val p = AutoStopPolicy()
        p.update(0, 1, true, false, 60)
        p.update(20000, 1, true, true, 60)
        p.update(50000, 1, true, false, 60)
        assertFalse(p.update(109999, 1, true, false, 60))
        assertTrue(p.update(110000, 1, true, false, 60))
    }
    @Test fun eventRestartsQuietPeriodEvenWhenMarkerSuppressed() {
        val p = AutoStopPolicy()
        p.update(0, 1, true, false, 60)
        p.event()
        p.update(45000, 1, true, false, 60)
        assertFalse(p.update(60000, 1, true, false, 60))
        assertTrue(p.update(105000, 1, true, false, 60))
    }
    @Test fun unknownTelemetryPausesAndRequiresFullFreshPeriod() {
        val p = AutoStopPolicy()
        p.update(0, 1, true, false, 60)
        assertFalse(p.update(65000, 1, true, null, 60))
        p.update(70000, 1, true, false, 60)
        assertFalse(p.update(129999, 1, true, false, 60))
        assertTrue(p.update(130000, 1, true, false, 60))
    }
    @Test fun manualClipDisconnectPauseAndNewClipCannotInheritTimer() {
        val p = AutoStopPolicy()
        p.update(0, 1, true, false, 60)
        assertFalse(p.update(60000, 0, true, false, 60))
        assertFalse(p.update(70000, 2, false, false, 60))
        assertFalse(p.update(80000, 2, true, false, 60))
        assertFalse(p.update(139999, 2, true, false, 60))
        assertTrue(p.update(140000, 2, true, false, 60))
    }
    @Test fun customDelayAndChangingSettingsResetTimer() {
        val p = AutoStopPolicy()
        p.update(0, 1, true, false, 60)
        assertFalse(p.update(20000, 1, true, false, 10))
        assertTrue(p.update(30000, 1, true, false, 10))
    }
    @Test fun originNeedsConfirmedStartAndExpiresOrCancels() {
        val p = RecordingOrigin()
        p.requestStart(0)
        assertEquals(0L, p.clip)
        p.cancel()
        p.status(true, 1000)
        assertEquals(0L, p.clip)
        p.status(false, 2000)
        p.requestStart(3000)
        p.status(true, 19000)
        assertEquals(0L, p.clip)
    }
    @Test fun confirmedClipHasUniqueIdentityAndManualOverrideRevokesIt() {
        val p = RecordingOrigin()
        p.requestStart(0)
        assertEquals(RecordingOrigin.Feedback.STARTED, p.status(true, 1000))
        val first = p.clip
        assertTrue(first > 0)
        p.cancel()
        assertEquals(0L, p.clip)
        p.status(false, 2000)
        p.requestStart(3000)
        p.status(true, 4000)
        assertTrue(p.clip > first)
        p.requestStop()
        assertEquals(RecordingOrigin.Feedback.STOPPED, p.status(false, 5000))
        assertEquals(0L, p.clip)
    }
    @Test fun cameraStopStartBetweenStatusPacketsRevokesOwnershipWhenTimerResets() {
        val p = RecordingOrigin()
        p.requestStart(0)
        p.status(true, 1000, 1)
        p.status(true, 30000, 30)
        assertTrue(p.clip > 0)
        p.status(true, 31000, 0)
        assertEquals(0L, p.clip)
    }
    @Test fun photosNeverBurstOrReplaySkippedEvents() {
        val p = AutomaticPhotoPolicy()
        assertFalse(p.request(0, false, 120))
        assertTrue(p.request(1, true, 120))
        assertFalse(p.request(1, true, 120))
        assertFalse(p.request(120000, true, 120))
        assertTrue(p.request(120001, true, 120))
    }
    @Test fun photoIntervalChangesApplyToLastAcceptedRequest() {
        val p = AutomaticPhotoPolicy()
        assertTrue(p.request(0, true, 120))
        assertFalse(p.request(60000, true, 120))
        assertTrue(p.request(60000, true, 60))
        assertFalse(p.request(180000, true, 300))
        assertFalse(p.request(360000, false, 300))
        assertTrue(p.request(360001, true, 300))
    }
    @Test fun photoIntervalUsesLongMillisecondsForLargeValues() {
        val p = AutomaticPhotoPolicy()
        assertTrue(p.request(0, true, 86400))
        assertFalse(p.request(86399999, true, 86400))
        assertTrue(p.request(86400000, true, 86400))
    }
}
