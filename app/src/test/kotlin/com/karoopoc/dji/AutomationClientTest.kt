package com.karoopoc.dji

import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AutomationClientTest {
    private lateinit var client: DjiOsmoBleClient
    private val context get() = RuntimeEnvironment.getApplication()
    private fun field(name: String) = DjiOsmoBleClient::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    @Suppress("UNCHECKED_CAST") private fun writes() = field("writeQueue").get(client) as ArrayDeque<Any>
    private fun origin() = field("recordingOrigin").get(client) as RecordingOrigin
    private fun mode(mode: AutomationMode) { DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_AUTOMATION_MODE, mode.name).commit() }
    @Before fun setup() {
        DjiSettings.resetAll(context)
        DjiSettings.preferences(context).edit().putBoolean("protocol_connected_once", true).putString("camera_address", "00:00:00:00:00:00").commit()
        mode(AutomationMode.RECORDING)
        client = DjiOsmoBleClient(context, {})
        field("cameraState").set(client, DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED))
        field("protocolConnected").setBoolean(client, true)
        field("lastCameraRxAtMs").setLong(client, SystemClock.elapsedRealtime())
        field("automationRideActive").setBoolean(client, true)
        field("writeInProgress").setBoolean(client, true)
    }
    @Test fun offlineEventsNeverQueueForReconnect() {
        field("protocolConnected").setBoolean(client, false)
        client.automaticStart(); mode(AutomationMode.PHOTOS); client.automaticPhoto(); idle()
        assertTrue(writes().isEmpty())
        assertTrue((field("pendingActionsAfterConnect").get(client) as ArrayDeque<*>).isEmpty())
    }
    @Test fun simultaneousStartsReserveOneOperationAndDoNotClaimRecordingYet() {
        client.automaticStart(); client.automaticStart(); idle()
        assertEquals(1, writes().size)
        assertEquals(0L, origin().clip)
        assertTrue((field("operationQueue").get(client) as List<*>).isEmpty())
    }
    @Test fun failedStartWriteRevokesOwnershipCandidate() {
        client.automaticStart(); idle()
        val write = writes().removeFirst()
        val callback = write.javaClass.getDeclaredField("onComplete").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (callback.get(write) as (Boolean) -> Unit)(false)
        origin().status(true, SystemClock.elapsedRealtime())
        assertEquals(0L, origin().clip)
    }
    @Test fun manualClipOrOldClipCannotBeStopped() {
        field("latestRecording").setBoolean(client, true)
        client.automaticStop(1); idle()
        assertTrue(writes().isEmpty())
        origin().requestStart(SystemClock.elapsedRealtime()); origin().status(true, SystemClock.elapsedRealtime())
        client.automaticStop(origin().clip + 1); idle()
        assertTrue(writes().isEmpty())
    }
    @Test fun pauseCancelsQueuedAutomaticCommandBeforeTransport() {
        client.automaticStart()
        client.setAutomationRideActive(false)
        idle()
        assertTrue(writes().isEmpty())
        assertEquals(0L, origin().clip)
    }
    @Test fun photosSkipVideoAndSimultaneousTriggersCannotBurst() {
        mode(AutomationMode.PHOTOS)
        field("latestRecording").setBoolean(client, true)
        client.automaticPhoto(); idle(); assertTrue(writes().isEmpty())
        field("latestRecording").setBoolean(client, false)
        client.automaticPhoto(); client.automaticPhoto(); idle()
        assertEquals(1, writes().size)
    }
    @Test fun renewedEffortCancelsQueuedStopWithoutLosingClipOwnership() {
        origin().requestStart(SystemClock.elapsedRealtime()); origin().status(true, SystemClock.elapsedRealtime())
        field("latestRecording").setBoolean(client, true)
        val clip = origin().clip
        client.automaticStop(clip)
        client.cancelPendingAutomaticStop()
        idle()
        assertTrue(writes().isEmpty())
        assertEquals(clip, origin().clip)
        assertFalse(origin().stopping)
    }
    @Test fun newDefaultsAndLegacyMigrationPreserveTriggerChoices() {
        val prefs = DjiSettings.preferences(context)
        prefs.edit().remove(DjiSettings.KEY_AUTOMATION_MODE).putBoolean(DjiSettings.KEY_AUTO_START_RECORDING, true)
            .putBoolean(DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS, true).putInt(DjiSettings.KEY_PEAK_POWER_WATTS, 650).commit()
        val s = DjiSettings.read(context)
        assertEquals(AutomationMode.RECORDING, s.mode)
        assertEquals(60, s.stopAfterSeconds); assertTrue(s.autoStop); assertTrue(s.addAutomaticHighlights)
        assertTrue(s.peakPowerHighlights); assertEquals(650, s.peakPowerWatts)
        prefs.edit().putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, false).commit()
        assertFalse(DjiSettings.read(context).autoHighlightsEnabled)
        assertEquals(AutomationMode.RECORDING, DjiSettings.read(context).mode)
    }
}
