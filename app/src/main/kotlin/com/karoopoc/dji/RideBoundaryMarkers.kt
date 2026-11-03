package com.karoopoc.dji

import io.hammerhead.karooext.models.RideState

/** Observed ride boundaries only; the first SDK value is a snapshot, not a start event. */
internal class RideBoundaryMarkers {
    enum class Marker { START, END }

    private var previous: RideState? = null

    fun onRideState(next: RideState, cameraRecording: Boolean, enabled: Boolean): Marker? {
        val before = previous
        previous = next
        if (!enabled || !cameraRecording) return null
        return when {
            before is RideState.Idle && next is RideState.Recording -> Marker.START
            (before is RideState.Recording || before is RideState.Paused) && next is RideState.Idle -> Marker.END
            else -> null
        }
    }
}
