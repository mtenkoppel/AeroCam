package com.karoopoc.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.PhotoCaptureState
import io.hammerhead.karooext.models.ViewConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
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
class SharedCameraButtonUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val camera = DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED,
        recordingDurationSeconds = 199)

    @Test fun videoAndCombinedCenterVisibleRecordingContentInsideTheButton() {
        val sizes = listOf(
            "half" to ViewConfig(30 to 15, 238 to 113, 24),
            "map" to ViewConfig(60 to 12, 478 to 125, 32),
            "small" to ViewConfig(60 to 15, 480 to 113, 26),
            "medium" to ViewConfig(60 to 20, 480 to 154, 32),
            "large" to ViewConfig(60 to 30, 480 to 189, 40),
            "full" to ViewConfig(60 to 60, 480 to 420, 40),
        )
        val captionHeight = context.resources.getDimensionPixelSize(R.dimen.widget_field_caption_height)
        for ((name, config) in sizes) for (caption in listOf(false, true)) {
            val (width, height) = config.viewSize
            val body = config.copy(viewSize = width to height - if (caption) captionHeight else 0)
            val recording = DjiWidgetViewState(camera.copy(recording = true))
            val highlight = recording.copy(camera = recording.camera.copy(highlightFeedback = "HIGHLIGHT"))
            for ((stateName, state) in listOf("recording" to recording, "highlight" to highlight)) {
                val video = DjiWidgetRenderer.video(context, body, state)
                val combined = DjiWidgetRenderer.videoPhoto(context, body, state)
                val first = video.apply(context, FrameLayout(context))
                val second = DjiWidgetRenderer.captionedField(context, R.string.dji_video_photo, caption, combined)
                    .apply(context, FrameLayout(context))
                layout(first, width, body.viewSize.second)
                layout(second, width, height)
                val videoButton = first.findViewById<View>(R.id.widgetVideoPrimary)
                val combinedButton = second.findViewById<View>(R.id.widgetVideoPrimary)
                val image = bitmap(combinedButton)
                assertTrue("$name/$caption/$stateName Video and Video + Photo parity", bitmap(videoButton).sameAs(image))
                assertEquals("03:19", combinedButton.findViewById<TextView>(R.id.widgetVideoTimer).text.toString())
                assertInkCentered(image, "$name/$caption/$stateName")
                if (name == "map" && caption) {
                    val dir = File("build/reports/settings-ui/shared-camera-buttons").apply { mkdirs() }
                    File(dir, "short-map-$stateName.png").outputStream().use {
                        bitmap(second).compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
    }

    @Test fun blockedShutterAndReconnectStayConsistentWhenViewsAreReapplied() {
        val config = ViewConfig(60 to 60, 480 to 420, 32)
        val ready = DjiWidgetViewState(camera)
        val blocked = DjiWidgetViewState(camera.copy(recording = true))
        val offline = DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED))
        val searching = offline.copy(connectPrompts = setOf(ConnectPrompt.CONTROL))
        val photo = DjiWidgetRenderer.photo(context, config, ready).apply(context, FrameLayout(context))
        val control = DjiWidgetRenderer.cameraControl(context, config, ready).apply(context, FrameLayout(context))

        for (state in listOf(blocked, ready, ready.copy(camera = camera.copy(photoState = PhotoCaptureState.CAPTURING)))) {
            DjiWidgetRenderer.photo(context, config, state).reapply(context, photo)
            DjiWidgetRenderer.cameraControl(context, config, state).reapply(context, control)
            for (root in listOf(photo, control)) {
                val button = root.findViewById<View>(R.id.widgetPhotoPrimary)
                assertEquals(!state.camera.recording, button.isEnabled)
                assertEquals(!state.camera.recording, button.hasOnClickListeners())
                if (state.camera.recording) {
                    val icon = root.findViewById<ImageView>(R.id.widgetPhotoIcon)
                    assertEquals(R.drawable.ic_widget_photo_muted, shadowOf(icon.drawable).createdFromResId)
                }
                if (state.camera.photoState == PhotoCaptureState.CAPTURING) {
                    assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widgetPhotoSpinner).visibility)
                }
            }
        }

        val video = DjiWidgetRenderer.video(context, config, offline).apply(context, FrameLayout(context))
        for (state in listOf(offline, searching, ready)) {
            DjiWidgetRenderer.video(context, config, state).reapply(context, video)
            DjiWidgetRenderer.photo(context, config, state).reapply(context, photo)
            DjiWidgetRenderer.cameraControl(context, config, state).reapply(context, control)
            val statuses = listOf(
                video.findViewById<TextView>(R.id.widgetVideoStatus),
                photo.findViewById<TextView>(R.id.widgetPhotoStatus),
                control.findViewById<TextView>(R.id.widgetControlTopMessageText),
            )
            if (state != ready) {
                val label = context.getString(if (state == searching) R.string.widget_searching else R.string.widget_connect)
                statuses.forEach { assertEquals(label, it.text.toString()) }
                assertFalse(control.findViewById<View>(R.id.widgetPhotoPrimary).hasOnClickListeners())
            } else {
                assertEquals(View.GONE, statuses[0].visibility)
                assertEquals(View.GONE, statuses[1].visibility)
            }
        }
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun bitmap(view: View): Bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        .also { view.draw(Canvas(it)) }

    private fun assertInkCentered(image: Bitmap, state: String) {
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = image.getPixel(x, y)
            if (Color.green(pixel) < 190 || Color.blue(pixel) < 190) continue
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        assertTrue("$state visible and inset: [$left, $top, $right, $bottom]", left > 0 && top > 0 &&
            right in left until image.width - 1 && bottom in top until image.height - 1)
        assertTrue("$state horizontally centered: $left..$right in ${image.width}",
            abs(left + right + 1 - image.width) <= 8)
        assertTrue("$state vertically centered: $top..$bottom in ${image.height}",
            abs(top + bottom + 1 - image.height) <= 8)
    }
}
