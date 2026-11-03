package com.karoopoc.dji

import io.hammerhead.karooext.models.RideState
import org.junit.Assert.*
import org.junit.Test

class RideBoundaryMarkersTest {
    @Test fun firstSdkSnapshotIsNeverInventedAsARideStart() {
        for (snapshot in listOf(RideState.Idle, RideState.Recording, RideState.Paused(false))) {
            assertNull(RideBoundaryMarkers().onRideState(snapshot, cameraRecording = true, enabled = true))
        }
    }

    @Test fun marksActualStartAndFinishOnceWithoutPauseResumeMarkers() {
        val policy = RideBoundaryMarkers()
        val events = listOf(RideState.Idle, RideState.Recording, RideState.Recording,
            RideState.Paused(true), RideState.Recording, RideState.Paused(false), RideState.Idle, RideState.Idle)
        assertEquals(listOf(null, RideBoundaryMarkers.Marker.START, null, null, null, null,
            RideBoundaryMarkers.Marker.END, null), events.map { policy.onRideState(it, true, true) })
    }

    @Test fun finishingWhileActivelyRidingAlsoMarksEnd() {
        val policy = RideBoundaryMarkers()
        policy.onRideState(RideState.Recording, true, true)
        assertEquals(RideBoundaryMarkers.Marker.END, policy.onRideState(RideState.Idle, true, true))
    }

    @Test fun recordingOrEnablingLaterDoesNotBackdateTheStart() {
        for (missingCamera in listOf(false, true)) {
            val policy = RideBoundaryMarkers()
            policy.onRideState(RideState.Idle, true, true)
            assertNull(policy.onRideState(RideState.Recording, !missingCamera, missingCamera))
            assertNull(policy.onRideState(RideState.Recording, true, true))
            assertNull(policy.onRideState(RideState.Paused(false), true, true))
            assertNull(policy.onRideState(RideState.Recording, true, true))
        }
    }

    @Test fun endRequiresCameraRecordingAndEnabledSettingWithoutDeferredRetry() {
        for (cameraRecording in listOf(false, true)) for (enabled in listOf(false, true)) {
            val policy = RideBoundaryMarkers()
            policy.onRideState(RideState.Recording, true, true)
            assertEquals(if (cameraRecording && enabled) RideBoundaryMarkers.Marker.END else null,
                policy.onRideState(RideState.Idle, cameraRecording, enabled))
            assertNull(policy.onRideState(RideState.Idle, true, true))
        }
    }
}
