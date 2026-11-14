package com.karoopoc.dji

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
        client.setAutomationRideActive(true)
        field("writeInProgress").setBoolean(client, true)
    }

    @Test fun lapToggleUsesCameraStatusIncludingExternallyStartedRecording() {
        shadowOf(context).declareComponentUnbindable(
            ComponentName.createRelative("io.hammerhead.appstore", ".service.AppStoreService"),
        )
        val service = Robolectric.buildService(DjiCameraService::class.java).create().get()
        DjiCameraService::class.java.getDeclaredField("client").apply { isAccessible = true }.set(service, client)
        val toggle = Intent(DjiCameraService.ACTION_RECORD_TOGGLE)
            .putExtra(DjiCameraService.EXTRA_REQUIRE_CAMERA_STATUS, true)
        val handleStatus = DjiOsmoBleClient::class.java
            .getDeclaredMethod("handleCameraStatus", DjiProtocol.Frame::class.java).apply { isAccessible = true }

        fun reportVideoStatus(status: Byte) {
            val payload = ByteArray(38)
            payload[0] = 1 // Video mode
            payload[1] = status
            handleStatus.invoke(
                client,
                DjiProtocol.Frame(byteArrayOf(), 0, 1, DjiProtocol.CMD_SET_CAMERA, DjiProtocol.CMD_ID_CAMERA_STATUS_PUSH, payload),
            )
        }

        service.onStartCommand(toggle, 0, 1)
        assertTrue(writes().isEmpty())

        reportVideoStatus(3) // Recording began on the camera itself.
        service.onStartCommand(toggle, 0, 2)
        val stopWrite = writes().removeFirst()
        val frame = stopWrite.javaClass.getDeclaredField("frame").apply { isAccessible = true }
        val stop = DjiProtocol.parseFrame(frame.get(stopWrite) as ByteArray)!!
        assertEquals(DjiProtocol.CMD_ID_RECORD_CONTROL, stop.cmdId)
        assertEquals(1, stop.payload[4].toInt())
        val onComplete = stopWrite.javaClass.getDeclaredField("onComplete").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (onComplete.get(stopWrite) as (Boolean) -> Unit)(true)

        reportVideoStatus(0)
        service.onStartCommand(toggle, 0, 3)
        val modeSwitch = DjiProtocol.parseFrame(frame.get(writes().removeFirst()) as ByteArray)!!
        assertEquals(DjiProtocol.CMD_ID_MODE_SWITCH, modeSwitch.cmdId)
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
    @Test fun autoPausePreservesOwnedClipAndAllowsStoppingButNotNewCaptures() {
        origin().requestStart(SystemClock.elapsedRealtime()); origin().status(true, SystemClock.elapsedRealtime())
        field("latestRecording").setBoolean(client, true)
        val clip = origin().clip
        client.setAutomationRideActive(true, allowCapture = false)
        assertEquals(clip, origin().clip)
        client.automaticStop(clip); idle()
        assertEquals(1, writes().size)
        assertTrue(origin().stopping)
    }
    @Test fun autoPauseCancelsPendingStartAndBlocksPhotos() {
        client.automaticStart()
        client.setAutomationRideActive(true, allowCapture = false)
        idle()
        assertTrue(writes().isEmpty())
        client.automaticStart(); mode(AutomationMode.PHOTOS); client.automaticPhoto(); idle()
        assertTrue(writes().isEmpty())
        origin().status(true, SystemClock.elapsedRealtime())
        assertEquals(0L, origin().clip)
    }
    @Test fun autoPauseAndResumeNeverClaimManualRecording() {
        field("latestRecording").setBoolean(client, true)
        origin().status(true, SystemClock.elapsedRealtime())
        client.setAutomationRideActive(true, allowCapture = false)
        client.setAutomationRideActive(true)
        client.automaticStop(1); idle()
        assertEquals(0L, origin().clip)
        assertTrue(writes().isEmpty())
    }
    @Test fun manualPauseStillRevokesConfirmedOwnership() {
        origin().requestStart(SystemClock.elapsedRealtime()); origin().status(true, SystemClock.elapsedRealtime())
        client.setAutomationRideActive(false)
        assertEquals(0L, origin().clip)
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
