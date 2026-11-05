package com.karoopoc.dji

import android.os.Looper
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
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
