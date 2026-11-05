package com.karoopoc.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.karoopoc.R
import com.karoopoc.dji.*
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w256dp-h427dp-port-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomationWidgetUiTest {
    @Test fun automaticStatesUseSharedSublinesAndManualClipsHaveNoStopPromise() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_AUTOMATION_MODE, AutomationMode.RECORDING.name).commit()
        val idle = DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED)
        val recording = idle.copy(recording = true, automaticRecordingId = 1, recordingDurationSeconds = 199)
        val states = listOf(
            Triple("idle", idle, "AUTO START"),
            Triple("started", recording.copy(automationFeedback = "STARTED"), "REC STARTED"),
            Triple("recording", recording, "AUTO STOP"),
            Triple("highlight", recording.copy(highlightFeedback = "HIGHLIGHT"), "★ HIGHLIGHT"),
            Triple("manual", recording.copy(automaticRecordingId = 0), ""),
            Triple("stopped", idle.copy(automationFeedback = "STOPPED"), "REC STOPPED"),
        )
        for ((size, config) in listOf("half" to ViewConfig(30 to 15, 238 to 85, 24), "map" to ViewConfig(60 to 15, 480 to 97, 32), "full" to ViewConfig(60 to 60, 480 to 420, 40))) {
            for ((name, camera, expected) in states) {
                val rv = DjiWidgetRenderer.video(context, config, DjiWidgetViewState(camera))
                val view = rv.apply(context, FrameLayout(context))
                view.measure(View.MeasureSpec.makeMeasureSpec(config.viewSize.first, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(config.viewSize.second, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, config.viewSize.first, config.viewSize.second)
                val line = view.findViewById<TextView>(if (camera.recording) R.id.widgetVideoMeta else R.id.widgetVideoIdleMeta)
                assertEquals("$size/$name", expected, line.text.toString())
                assertEquals(if (expected.isEmpty()) View.GONE else View.VISIBLE, line.visibility)
                if (line.visibility == View.VISIBLE) {
                    assertTrue("$size/$name fits width", line.paint.measureText(line.text.toString()) <= line.width)
                    val loc = IntArray(2); line.getLocationOnScreen(loc)
                    assertTrue("$size/$name fits height", loc[1] >= 0 && loc[1] + line.height <= view.height)
                }
                val folder = File("build/reports/settings-ui/automation-widgets").apply { mkdirs() }
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(folder, "$size-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
