package com.karoopoc.dji

import android.content.Intent
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.RideState
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
class RideMarkersIntegrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var integration: DjiKarooIntegration

    @Before fun setup() {
        DjiSettings.resetAll(context)
        DjiSettings.setBoolean(context, DjiSettings.KEY_RIDE_BOOKMARKS, true)
        integration = DjiKarooIntegration(context, KarooSystemService(context))
    }

    private fun ride(state: RideState) {
        DjiKarooIntegration::class.java.getDeclaredMethod("onRideState", RideState::class.java)
            .apply { isAccessible = true }.invoke(integration, state)
    }

    private fun camera(recording: Boolean, connected: Boolean = true) {
        DjiKarooIntegration::class.java.getDeclaredMethod("onCameraState", DjiCameraState::class.java)
            .apply { isAccessible = true }.invoke(integration,
                DjiCameraState(connection = if (connected) CameraConnectionState.CONNECTED else CameraConnectionState.DISCONNECTED,
                    configured = true, recording = recording))
    }

    private fun requests(): List<Intent> = generateSequence { shadowOf(context).nextStartedService }.toList()

    @Test fun actualBoundaryCommandsBypassCooldownAndNeverStartRecording() {
        camera(true)
        ride(RideState.Idle)
        ride(RideState.Recording)
        ride(RideState.Paused(false))
        ride(RideState.Recording)
        ride(RideState.Paused(true))
        ride(RideState.Idle)
        val commands = requests()
        val highlights = commands.filter { it.action == DjiCameraService.ACTION_HIGHLIGHT }
        assertEquals(2, highlights.size)
        assertTrue(highlights.all { !it.getBooleanExtra(DjiCameraService.EXTRA_HIGHLIGHT_COOLDOWN_LIMITED, true) })
        assertFalse(commands.any { it.action == DjiCameraService.ACTION_RECORD_START })
    }

    @Test fun masterAndIndividualSwitchEachPreventBothBoundaryCommands() {
        for (key in listOf(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, DjiSettings.KEY_RIDE_BOOKMARKS)) {
            setup()
            DjiSettings.setBoolean(context, key, false)
            camera(true)
            ride(RideState.Idle)
            ride(RideState.Recording)
            ride(RideState.Idle)
            assertFalse(requests().any { it.action == DjiCameraService.ACTION_HIGHLIGHT })
        }
    }

    @Test fun lateCameraRecordingDoesNotCreateAStartMarkerOrUseAutoStart() {
        DjiSettings.setBoolean(context, DjiSettings.KEY_AUTO_START_RECORDING, true)
        camera(false)
        ride(RideState.Idle)
        ride(RideState.Recording)
        camera(true)
        ride(RideState.Paused(false))
        ride(RideState.Recording)
        assertFalse(requests().any {
            it.action == DjiCameraService.ACTION_HIGHLIGHT || it.action == DjiCameraService.ACTION_RECORD_START
        })
    }

    @Test fun disconnectedCameraWithLastKnownRecordingStateDoesNotQueueBoundaryMarkers() {
        camera(recording = true, connected = false)
        ride(RideState.Idle)
        ride(RideState.Recording)
        ride(RideState.Paused(false))
        ride(RideState.Idle)
        camera(recording = true)
        assertFalse(requests().any { it.action == DjiCameraService.ACTION_HIGHLIGHT })
    }
}
