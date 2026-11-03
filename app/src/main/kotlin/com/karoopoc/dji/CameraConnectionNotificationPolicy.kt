package com.karoopoc.dji

/** Observes connection edges without changing the camera's connection lifecycle. */
internal class CameraConnectionNotificationPolicy {
    private var wasConnected = false

    fun onConnectionState(connected: Boolean): Boolean {
        val notify = connected && !wasConnected
        wasConnected = connected
        return notify
    }
}
