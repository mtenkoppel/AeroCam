package com.karoopoc.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.karoopoc.DjiTheme
import com.karoopoc.MainActivity
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.DjiSettings
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w256dp-h427dp-port-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CamControlUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val camera = DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED,
        batteryPercent = 60, remainingCapacityMb = 14848, recordingDurationSeconds = 885, keepAwakeEnabled = true)
    private fun render(height: Int, state: DjiWidgetViewState = DjiWidgetViewState(camera), interactive: Boolean = true): View {
        val config = ViewConfig(Pair(60, if (height >= 280) 60 else if (height >= 170) 30 else if (height >= 130) 20 else 15), Pair(480, height), 32)
        val view = DjiWidgetRenderer.cameraControl(context, config, state, interactive).apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 480, height)
        return view
    }
    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/reports/settings-ui/cam-control").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun fourRowsShareRemainingHeightAndPreserveBatteryColorAndRecordingState() {
        for (theme in listOf("dark", "light")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE, theme).commit()
            for (height in listOf(280, 320, 408, 420, 600)) {
                val view = render(height)
                val awake = view.findViewById<View>(R.id.widgetControlFullKeepAwake)
                val settings = view.findViewById<View>(R.id.widgetControlMore)
                val video = view.findViewById<View>(R.id.widgetControlFull)
                val photo = view.findViewById<View>(R.id.widgetControlPhoto)
                val power = view.findViewById<View>(R.id.widgetControlFullSleep)
                val rows = listOf(awake.height, settings.height, video.height, photo.height, power.height)
                assertTrue("$theme/$height equal rows: $rows", rows.max() - rows.min() <= 1)
                val gap = context.resources.getDimensionPixelSize(R.dimen.widget_control_group_gap)
                val photoGap = (photo.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin
                assertEquals(gap, video.top - view.findViewById<View>(R.id.widgetControlFullUtility).bottom)
                assertEquals(gap, power.top - photo.bottom)
                assertEquals(photoGap, photo.top - video.bottom)
                assertTrue(abs(4 * video.height + 2 * gap + photoGap - (height - view.paddingTop - view.paddingBottom)) <= 3)
                assertEquals(DjiTheme.palette(context).textPrimary, view.findViewById<TextView>(R.id.widgetControlFullBattery).currentTextColor)
                save(view, "$theme-xl-$height-ready")
                if (height in listOf(320, 408, 600)) {
                    save(render(height, DjiWidgetViewState(camera.copy(batteryPercent = 15))), "$theme-xl-$height-low-battery")
                    save(render(height, DjiWidgetViewState(camera.copy(recording = true, highlightFeedback = "HIGHLIGHT"))), "$theme-xl-$height-recording")
                    save(render(height, DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED))), "$theme-xl-$height-offline")
                }
            }
            for (height in listOf(113, 154, 189)) {
                save(render(height), "$theme-compact-$height-ready")
                save(render(height, DjiWidgetViewState(camera.copy(batteryPercent = 15))), "$theme-compact-$height-low-battery")
                save(render(height, DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED))), "$theme-compact-$height-offline")
            }
            val low = render(420, DjiWidgetViewState(camera.copy(batteryPercent = 15)))
            assertEquals(DjiTheme.palette(context).batteryLow, low.findViewById<TextView>(R.id.widgetControlFullBattery).currentTextColor)
            save(low, "$theme-xl-low-battery")
            val recording = render(420, DjiWidgetViewState(camera.copy(recording = true, highlightFeedback = "HIGHLIGHT")))
            assertEquals(View.VISIBLE, recording.findViewById<View>(R.id.widgetControlVideoHighlight).visibility)
            assertEquals(View.VISIBLE, recording.findViewById<View>(R.id.widgetControlVideoRecording).visibility)
            assertEquals("14:45", recording.findViewById<TextView>(R.id.widgetControlVideoTimer).text.toString())
            assertFalse(recording.findViewById<View>(R.id.widgetControlPhoto).hasOnClickListeners())
            save(recording, "$theme-xl-recording")
            save(render(420, DjiWidgetViewState(camera, sleepConfirmation = true)), "$theme-xl-confirmation")
            save(render(420, DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED))), "$theme-xl-offline")
        }
    }
    @Test fun compactAndOfflineSettingsOpenAppWhileKeepAwakeKeepsItsExistingAction() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        for (height in listOf(113, 154, 189)) {
            val view = render(height, DjiWidgetViewState(camera.copy(batteryPercent = 15)))
            assertEquals(DjiTheme.palette(context).batteryLow, view.findViewById<TextView>(R.id.widgetControlBattery).currentTextColor)
            val settings = view.findViewById<View>(R.id.widgetControlSettings)
            assertEquals(View.VISIBLE, settings.visibility)
            assertTrue(settings.performClick())
            assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
            assertTrue(view.findViewById<View>(R.id.widgetControlKeepAwake).performClick())
            assertEquals(DjiWidgetActionReceiver.ACTION_KEEP_AWAKE_TOGGLE, shadowOf(context).broadcastIntents.last().action)
            save(view, "compact-$height-low-battery")
            val offline = render(height, DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED)))
            assertTrue(offline.findViewById<View>(R.id.widgetControlMessageSettings).performClick())
            assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
            save(offline, "compact-$height-offline")
        }
        host.pause().stop().destroy()
    }
    @Test fun feedbackExpiryAndDisconnectReapplyRestoreExistingControlState() {
        val config = ViewConfig(Pair(60,60), Pair(480,420), 32)
        val view = render(420, DjiWidgetViewState(camera.copy(recording = true, highlightFeedback = "HIGHLIGHT")))
        val timer = view.findViewById<TextView>(R.id.widgetControlVideoTimer)
        val group = view.findViewById<View>(R.id.widgetControlVideoRecording)
        assertTrue(group.paddingBottom > 0)
        DjiWidgetRenderer.cameraControl(context, config, DjiWidgetViewState(camera.copy(recording = true))).reapply(context, view)
        assertEquals(0, group.paddingBottom)
        assertEquals(28f * context.resources.displayMetrics.scaledDensity, timer.textSize, .1f)
        DjiWidgetRenderer.cameraControl(context, config, DjiWidgetViewState(camera)).reapply(context, view)
        assertTrue(view.findViewById<View>(R.id.widgetControlFullSleep).hasOnClickListeners())
        DjiWidgetRenderer.cameraControl(context, config,
            DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED))).reapply(context, view)
        assertFalse(view.findViewById<View>(R.id.widgetControlFullSleep).hasOnClickListeners())
    }
}
