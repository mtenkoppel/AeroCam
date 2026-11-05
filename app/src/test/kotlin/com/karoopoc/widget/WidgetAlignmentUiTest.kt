package com.karoopoc.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.karoopoc.DjiTheme
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.DjiSettings
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
class WidgetAlignmentUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val state = DjiWidgetViewState(DjiCameraState(configured = true,
        connection = CameraConnectionState.CONNECTED, recording = true,
        batteryPercent = 15, remainingCapacityMb = 14848, recordingDurationSeconds = 885,
        highlightFeedback = "HIGHLIGHT"))
    private fun config(alignment: ViewConfig.Alignment, full: Boolean = false) =
        ViewConfig(Pair(60, if (full) 60 else 30), Pair(480, if (full) 408 else 189), 32, alignment)
    private fun checkCenteredText(view: View, id: Int) {
        val text = view.findViewById<TextView>(id)
        assertEquals(Gravity.CENTER_HORIZONTAL, text.gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
        assertEquals(View.TEXT_ALIGNMENT_GRAVITY, text.textAlignment)
    }
    private fun save(view: View, height: Int, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 480, height)
        val bitmap = Bitmap.createBitmap(480, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/reports/settings-ui/alignment").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun recordingValuesAndStatusesStayCenteredAcrossKarooAlignmentsAndReapply() {
        val parent = FrameLayout(context)
        var video: View? = null
        var combined: View? = null
        var photo: View? = null
        for (alignment in ViewConfig.Alignment.entries) {
            val c = config(alignment)
            val videoViews = DjiWidgetRenderer.video(context, c, state)
            if (video == null) video = videoViews.apply(context, parent) else videoViews.reapply(context, video)
            for (id in listOf(R.id.widgetVideoTimer, R.id.widgetVideoMeta, R.id.widgetVideoStatus))
                checkCenteredText(video!!, id)
            assertEquals("14:45", video!!.findViewById<TextView>(R.id.widgetVideoTimer).text.toString())
            val combinedViews = DjiWidgetRenderer.videoPhoto(context, c, state)
            if (combined == null) combined = combinedViews.apply(context, parent) else combinedViews.reapply(context, combined)
            checkCenteredText(combined!!, R.id.widgetVideoTimer)
            checkCenteredText(combined!!, R.id.widgetVideoMeta)
            val photoViews = DjiWidgetRenderer.photo(context, c, state)
            if (photo == null) photo = photoViews.apply(context, parent) else photoViews.reapply(context, photo)
            checkCenteredText(photo!!, R.id.widgetPhotoStatus)
            save(video!!, 189, "video-$alignment")
        }
    }
    @Test fun cameraDataSlotsKeepTheirLayoutWhileRecordingFeedbackStaysCentered() {
        for (theme in listOf("light", "dark")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE, theme).commit()
            var full: View? = null
            var buttonPositions: List<Int>? = null
            for (alignment in ViewConfig.Alignment.entries) {
                val compact = DjiWidgetRenderer.cameraControl(context, config(alignment), state)
                    .apply(context, FrameLayout(context))
                checkCenteredText(compact, R.id.widgetControlBattery)
                checkCenteredText(compact, R.id.widgetControlStorage)
                save(compact, 189, "$theme-compact-$alignment")
                val remote = DjiWidgetRenderer.cameraControl(context, config(alignment, true), state)
                if (full == null) full = remote.apply(context, FrameLayout(context)) else remote.reapply(context, full)
                for (id in listOf(R.id.widgetControlFullBatteryGroup, R.id.widgetControlFullStorageGroup,
                    R.id.widgetControlVideoRecording)) {
                    assertEquals(Gravity.CENTER_HORIZONTAL, full!!.findViewById<LinearLayout>(id).gravity and Gravity.HORIZONTAL_GRAVITY_MASK)
                }
                checkCenteredText(full!!, R.id.widgetControlVideoFeedback)
                assertEquals(DjiTheme.widgetPalette(context).textPrimary,
                    full!!.findViewById<TextView>(R.id.widgetControlFullBattery).currentTextColor)
                save(full!!, 408, "$theme-full-$alignment")
                val positions = listOf(R.id.widgetControlFullKeepAwake, R.id.widgetControlMore,
                    R.id.widgetControlVideo, R.id.widgetControlPhoto, R.id.widgetControlFullSleep)
                    .flatMap { id -> full!!.findViewById<View>(id).let { listOf(it.left, it.top, it.width, it.height) } }
                if (buttonPositions == null) buttonPositions = positions else assertEquals(buttonPositions, positions)
            }
        }
    }
}
