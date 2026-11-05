package com.karoopoc.widget

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraMode
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.DjiSettings
import com.karoopoc.dji.PhotoCaptureState
import io.hammerhead.karooext.models.ViewConfig
import java.io.File

/** Native equivalence checks: no camera commands, BLE permissions or activity launch. */
class WidgetRenderingInstrumentation : Instrumentation() {
    private var captureCamControl = false

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        captureCamControl = arguments?.getString("camControl") == "true"
        start()
    }

    override fun onStart() {
        try {
            check(targetContext.packageName.endsWith(".capture")) { "Build with -PwidgetCapture=true; do not run against the rider app" }
            var comparisons = 0
            val sizes = listOf(
                "large" to ViewConfig(Pair(60, 30), Pair(480, 189), 40),
                "medium" to ViewConfig(Pair(60, 20), Pair(480, 154), 32),
                "small" to ViewConfig(Pair(60, 15), Pair(480, 113), 26),
                "half" to ViewConfig(Pair(30, 15), Pair(238, 113), 24),
                "full" to ViewConfig(Pair(60, 60), Pair(480, 420), 40),
            )
            val camera = DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED,
                batteryPercent = 60, remainingCapacityMb = 14848, recordingDurationSeconds = 885)
            if (captureCamControl) {
                runOnMainSync { captureCamControl(sizes, camera) }
                finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "Cam Control screenshots saved. No camera actions sent.\n") })
                return
            }
            val ready = DjiWidgetViewState(camera)
            val cases = linkedMapOf(
                "ready" to ready,
                "offline" to ready.copy(camera = camera.copy(connection = CameraConnectionState.DISCONNECTED)),
                "unpaired" to ready.copy(camera = camera.copy(configured = false, connection = CameraConnectionState.DISCONNECTED)),
                "connecting-video" to ready.copy(camera = camera.copy(connection = CameraConnectionState.SEARCHING), connectPrompts = setOf(ConnectPrompt.VIDEO)),
                "connecting-photo" to ready.copy(camera = camera.copy(connection = CameraConnectionState.SEARCHING), connectPrompts = setOf(ConnectPrompt.PHOTO)),
                "photo-pending" to ready.copy(photoAwaitingResponse = "."),
                "photo-capturing" to ready.copy(camera = camera.copy(photoState = PhotoCaptureState.CAPTURING)),
                "photo-success" to ready.copy(camera = camera.copy(photoState = PhotoCaptureState.COMPLETE)),
                "start-pending" to ready.copy(videoAwaitingResponse = "."),
                "both-pending" to ready.copy(videoAwaitingResponse = ".", photoAwaitingResponse = "."),
                "recording" to ready.copy(camera = camera.copy(recording = true)),
                "highlight" to ready.copy(camera = camera.copy(recording = true, highlightFeedback = "HIGHLIGHT")),
                "stop-pending" to ready.copy(camera = camera.copy(recording = true), videoAwaitingResponse = "."),
            )
            runOnMainSync {
                for (theme in listOf("dark", "light")) {
                    val context = ContextThemeWrapper(targetContext, R.style.AppTheme).apply {
                        applyOverrideConfiguration(Configuration().apply {
                            uiMode = if (theme == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                        })
                    }
                    for (captionsHidden in listOf(false, true)) {
                        DjiSettings.preferences(context).edit().putBoolean(DjiSettings.KEY_HIDE_FIELD_CAPTIONS, captionsHidden).commit()
                        for ((size, config) in sizes) for ((scenario, state) in cases) {
                            val combinedRemote = DjiWidgetRenderer.videoPhoto(context, config, state)
                            val combined = inflate(context, combinedRemote, config.viewSize.first, config.viewSize.second)
                            if (state.camera.recording) {
                                val reference = inflate(context, DjiWidgetRenderer.video(context, config, state), config.viewSize.first, config.viewSize.second)
                                assertPixels(combined, reference, "$theme/$size/$scenario recording")
                                check(combined.findViewById<View>(R.id.widgetPhotoPrimary) == null)
                                comparisons++
                            } else {
                                val slotWidth = (config.viewSize.first - 20 - context.resources.getDimensionPixelSize(R.dimen.widget_combined_gap)) / 2
                                val slotConfig = config.copy(gridSize = Pair(config.gridSize.first / 2, config.gridSize.second), viewSize = Pair(slotWidth, config.viewSize.second - 20))
                                for ((id, remote) in listOf(
                                    R.id.widgetVideoPrimary to DjiWidgetRenderer.video(context, slotConfig, state),
                                    R.id.widgetPhotoPrimary to DjiWidgetRenderer.photo(context, slotConfig, state),
                                )) {
                                    val actual = combined.findViewById<View>(id)
                                    // Standalone surfaces get the same available area; compare their
                                    // rendered pixels (including pressed selectors), not implementation strings.
                                    val reference = inflate(context, remote, actual.width + 20, actual.height + 20).findViewById<View>(id)
                                    assertPixels(actual, reference, "$theme/$size/$scenario/$id")
                                    actual.isPressed = true; reference.isPressed = true
                                    assertPixels(actual, reference, "$theme/$size/$scenario/$id pressed")
                                    actual.isPressed = false
                                    check(actual.hasOnClickListeners())
                                    comparisons += 2
                                }
                                val beforeReapply = bitmap(combined)
                                combinedRemote.reapply(context, combined)
                                combined.measure(View.MeasureSpec.makeMeasureSpec(config.viewSize.first, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(config.viewSize.second, View.MeasureSpec.EXACTLY))
                                combined.layout(0, 0, config.viewSize.first, config.viewSize.second)
                                check(combined.findViewById<FrameLayout>(R.id.widgetCombinedVideo).childCount == 1)
                                check(combined.findViewById<FrameLayout>(R.id.widgetCombinedPhoto).childCount == 1)
                                val afterReapply = bitmap(combined)
                                check(beforeReapply.sameAs(afterReapply)) { "Reapply changed rendering: $theme/$size/$scenario" }
                                beforeReapply.recycle(); afterReapply.recycle()
                                comparisons++
                            }
                            if (state.videoAwaitingResponse != null) check(combined.findViewById<View>(R.id.widgetVideoSpinner).visibility == View.VISIBLE)
                            if (!state.camera.recording && (state.photoAwaitingResponse != null || state.camera.photoState == PhotoCaptureState.CAPTURING)) {
                                check(combined.findViewById<View>(R.id.widgetPhotoSpinner).visibility == View.VISIBLE)
                            }
                            if (!captionsHidden) save(combined, "$theme-$size-$scenario")
                        }
                    }
                }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: $comparisons native pixel comparisons; 5 sizes, 2 themes, captions on/off, 13 states. No camera actions sent.\n") })
        } catch (t: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${t.stackTraceToString()}\n") })
        }
    }

    private fun captureCamControl(sizes: List<Pair<String, ViewConfig>>, camera: DjiCameraState) {
        val video = camera.copy(cameraMode = DjiCameraMode.VIDEO, videoResolution = 16, videoFps = 6, keepAwakeEnabled = true)
        val photo = video.copy(cameraMode = DjiCameraMode.PHOTO, videoResolution = null, videoFps = null)
        val offline = video.copy(connection = CameraConnectionState.DISCONNECTED)
        val cases = linkedMapOf(
            "ready" to DjiWidgetViewState(video),
            "refresh" to DjiWidgetViewState(photo),
            "switching" to DjiWidgetViewState(photo, refreshingVideoSettings = true),
            "offline" to DjiWidgetViewState(offline),
            "searching" to DjiWidgetViewState(offline, connectPrompts = setOf(ConnectPrompt.CONTROL)),
            "low-battery" to DjiWidgetViewState(video.copy(batteryPercent = 15)),
            "recording" to DjiWidgetViewState(video.copy(recording = true, highlightFeedback = "HIGHLIGHT")),
        )
        for (theme in listOf("light", "dark")) {
            val context = ContextThemeWrapper(targetContext, R.style.AppTheme).apply {
                applyOverrideConfiguration(Configuration().apply {
                    uiMode = if (theme == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                })
            }
            for ((name, config) in sizes) for ((scenario, state) in cases) {
                val caption = context.resources.getDimensionPixelSize(R.dimen.widget_field_caption_height)
                val body = config.copy(viewSize = config.viewSize.first to config.viewSize.second - caption)
                val remote = DjiWidgetRenderer.captionedField(
                    context, R.string.dji_power, true, DjiWidgetRenderer.cameraControl(context, body, state, interactive = false),
                )
                save(inflate(context, remote, config.viewSize.first, config.viewSize.second), "cam-control-$theme-$name-$scenario")
            }
        }
    }

    private fun inflate(context: Context, remote: RemoteViews, width: Int, height: Int): View {
        val view = remote.apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return view
    }

    private fun bitmap(view: View) = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    private fun assertPixels(actual: View, expected: View, label: String) {
        val a = bitmap(actual); val b = bitmap(expected)
        val equal = a.sameAs(b)
        a.recycle(); b.recycle()
        check(equal) { "Native rendering differs: $label (${actual.width}x${actual.height} vs ${expected.width}x${expected.height})" }
    }
    private fun save(view: View, name: String) {
        val folder = File(targetContext.getExternalFilesDir(null), "widget-verification").apply { mkdirs() }
        val image = bitmap(view)
        File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
