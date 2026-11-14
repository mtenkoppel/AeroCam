package com.karoopoc.dji

import android.os.Looper
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Lap
import io.hammerhead.karooext.models.RideState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class AutomationIntegrationTest {
    @Test fun lapShortcutOverridesLapAutomationAndCanBeTurnedOffDuringARide() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        DjiSettings.setBoolean(context, DjiSettings.KEY_LAP_HIGHLIGHTS, true)
        val integration = DjiKarooIntegration(context, KarooSystemService(context))
        DjiKarooIntegration::class.java.getDeclaredMethod("onRideState", RideState::class.java)
            .apply { isAccessible = true }.invoke(integration, RideState.Recording)
        val lap = DjiKarooIntegration::class.java.getDeclaredMethod("onLap", Lap::class.java)
            .apply { isAccessible = true }
        DjiCameraController.publish(DjiCameraState(connection = CameraConnectionState.CONNECTED, recording = true))
        while (shadowOf(context).nextStartedService != null) { }

        fun lapActions(number: Int): List<android.content.Intent> {
            lap.invoke(integration, Lap(number, 30_000L, "manual"))
            shadowOf(Looper.getMainLooper()).idle()
            return generateSequence { shadowOf(context).nextStartedService }.toList()
        }

        val defaultAction = lapActions(1).single()
        assertEquals(DjiCameraService.ACTION_HIGHLIGHT, defaultAction.action)
        assertFalse(defaultAction.getBooleanExtra(DjiCameraService.EXTRA_HIGHLIGHT_COOLDOWN_LIMITED, true))

        DjiSettings.setBoolean(context, DjiSettings.KEY_HARDWARE_START_STOP, true)
        val shortcut = lapActions(2).single()
        assertEquals(DjiCameraService.ACTION_RECORD_TOGGLE, shortcut.action)
        assertTrue(shortcut.getBooleanExtra(DjiCameraService.EXTRA_REQUIRE_CAMERA_STATUS, false))

        DjiSettings.setBoolean(context, DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, false)
        assertEquals(DjiCameraService.ACTION_RECORD_TOGGLE, lapActions(3).single().action)

        DjiSettings.setBoolean(context, DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, true)
        DjiSettings.setBoolean(context, DjiSettings.KEY_HARDWARE_START_STOP, false)
        assertEquals(DjiCameraService.ACTION_HIGHLIGHT, lapActions(4).single().action)
    }

    @Test fun autoPauseRetainsStopPermissionWhileManualPauseCancelsAutomation() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        val integration = DjiKarooIntegration(context, KarooSystemService(context))
        val method = DjiKarooIntegration::class.java.getDeclaredMethod("onRideState", RideState::class.java)
            .apply { isAccessible = true }
        method.invoke(integration, RideState.Recording)
        while (shadowOf(context).nextStartedService != null) { }
        method.invoke(integration, RideState.Paused(auto = true))
        val automatic = generateSequence { shadowOf(context).nextStartedService }.toList()
        assertFalse(automatic.any { it.action == DjiCameraService.ACTION_AUTO_CANCEL })
        val state = automatic.single { it.action == DjiCameraService.ACTION_RIDE_STATE_CHANGED }
        assertTrue(state.getBooleanExtra("automationRideActive", false))
        assertFalse(state.getBooleanExtra("automationCaptureActive", true))
        method.invoke(integration, RideState.Paused(auto = false))
        val manual = generateSequence { shadowOf(context).nextStartedService }.toList()
        assertTrue(manual.any { it.action == DjiCameraService.ACTION_AUTO_CANCEL })
    }

    @Test fun freshSpeedCannotTriggerCaptureUsingStaleGradient() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_AUTOMATION_MODE, AutomationMode.RECORDING.name)
            .putBoolean(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, true).commit()
        val integration = DjiKarooIntegration(context, KarooSystemService(context))
        fun field(name: String) = DjiKarooIntegration::class.java.getDeclaredField(name).apply { isAccessible = true }
        fun sample(name: String, value: Double) = DjiKarooIntegration::class.java.getDeclaredMethod(name, Double::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(integration, value)
        field("rideState").set(integration, RideState.Recording)
        DjiCameraController.publish(DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED))
        @Suppress("UNCHECKED_CAST")
        val times = field("telemetryAt").get(integration) as MutableMap<String, Long>
        times[DataType.Type.ELEVATION_GRADE] = SystemClock.elapsedRealtime()
        sample("onGrade", -6.0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
        while (shadowOf(context).nextStartedService != null) { }
        times[DataType.Type.SPEED] = SystemClock.elapsedRealtime()
        sample("onSpeed", 20.0)
        shadowOf(Looper.getMainLooper()).idle()
        val stale = generateSequence { shadowOf(context).nextStartedService }.toList()
        assertFalse(stale.any { it.action == DjiCameraService.ACTION_AUTO_START })
        times[DataType.Type.ELEVATION_GRADE] = SystemClock.elapsedRealtime()
        sample("onGrade", -6.0)
        shadowOf(Looper.getMainLooper()).idle()
        val fresh = generateSequence { shadowOf(context).nextStartedService }.toList()
        assertEquals(1, fresh.count { it.action == DjiCameraService.ACTION_AUTO_START })
    }
}
