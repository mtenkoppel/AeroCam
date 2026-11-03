package com.karoopoc.dji

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraConnectionNotificationPolicyTest {
    @Test
    fun `searching and connecting do not notify, connected updates notify only once`() {
        val policy = CameraConnectionNotificationPolicy()
        // Disconnected, searching, connecting, connected, then telemetry updates.
        val notifications = listOf(false, false, false, true, true, true)
            .map(policy::onConnectionState)
        assertEquals(listOf(false, false, false, true, false, false), notifications)
    }

    @Test
    fun `reconnection notifies again without notifying on connection loss or failed attempts`() {
        val policy = CameraConnectionNotificationPolicy()
        val notifications = listOf(true, false, false, false, true, true)
            .map(policy::onConnectionState)
        assertEquals(listOf(true, false, false, false, true, false), notifications)
    }

    @Test
    fun `already connected camera is announced once when observation begins`() {
        val policy = CameraConnectionNotificationPolicy()
        assertEquals(true, policy.onConnectionState(true))
        assertEquals(false, policy.onConnectionState(true))
    }
}
