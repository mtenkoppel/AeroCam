package com.karoopoc.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
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
class WidgetAppearanceTest {
    @Test fun `every widget stays identical across host appearances and legacy preferences`() {
        val application = RuntimeEnvironment.getApplication()
        val preferences = DjiSettings.preferences(application)
        preferences.edit().clear().commit()
        val baselines = mutableMapOf<String, Bitmap>()
        val camera = DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED,
            batteryPercent = 60, remainingCapacityMb = 14848, remainingRecordingSeconds = 7200,
            recordingDurationSeconds = 885)
        val scenarios = mapOf(
            "ready" to DjiWidgetViewState(camera),
            "recording" to DjiWidgetViewState(camera.copy(recording = true)),
            "offline" to DjiWidgetViewState(camera.copy(connection = CameraConnectionState.DISCONNECTED)),
        )
        val sizes = mapOf(
            "large" to ViewConfig(Pair(60, 30), Pair(480, 189), 40),
            "half" to ViewConfig(Pair(30, 15), Pair(238, 113), 24),
            "full" to ViewConfig(Pair(60, 60), Pair(480, 420), 40),
        )
        val folder = File("build/reports/settings-ui").apply { mkdirs() }
        try {
            for (systemDark in listOf(false, true)) for (legacyMode in listOf(null, "system", "light", "dark")) {
                preferences.edit().putString(DjiSettings.KEY_THEME_MODE, legacyMode).commit()
                val context = ContextThemeWrapper(application.createConfigurationContext(Configuration().apply {
                    uiMode = if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }), R.style.AppTheme)
                assertEquals(Color.BLACK, DjiTheme.widgetPalette(context).widgetBackground)
                assertEquals(Color.WHITE, DjiTheme.widgetPalette(context).textPrimary)
                for ((size, config) in sizes) for ((scenario, state) in scenarios) {
                    val widgets = mapOf(
                        "video" to (R.id.widgetVideoRoot to DjiWidgetRenderer.video(context, config, state, false)),
                        "photo" to (R.id.widgetPhotoRoot to DjiWidgetRenderer.photo(context, config, state, false)),
                        "combined" to ((if (scenario != "ready") R.id.widgetVideoRoot else R.id.widgetVideoPhotoRoot) to
                            DjiWidgetRenderer.videoPhoto(context, config, state, false)),
                        "control" to (R.id.widgetControlRoot to DjiWidgetRenderer.cameraControl(context, config, state, false)),
                    )
                    for ((name, entry) in widgets) {
                        val (rootId, remote) = entry
                        val view = remote.apply(context, FrameLayout(context))
                        assertEquals("$name root must stay black", Color.BLACK,
                            (view.findViewById<View>(rootId).background as GradientDrawable).color!!.defaultColor)
                        view.measure(View.MeasureSpec.makeMeasureSpec(config.viewSize.first, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(config.viewSize.second, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, config.viewSize.first, config.viewSize.second)
                        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                        view.draw(Canvas(bitmap))
                        val key = "$name-$size-$scenario"
                        val baseline = baselines[key]
                        if (baseline == null) {
                            baselines[key] = bitmap
                            File(folder, "widget-$key.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } else {
                            assertTrue("$key must ignore host appearance and old $legacyMode preference", baseline.sameAs(bitmap))
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            baselines.values.forEach { it.recycle() }
            preferences.edit().clear().commit()
        }
    }
}
