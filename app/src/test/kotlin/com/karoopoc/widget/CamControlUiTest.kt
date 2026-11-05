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
import com.karoopoc.dji.DjiCameraMode
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
        batteryPercent = 60, remainingCapacityMb = 14848, recordingDurationSeconds = 885, keepAwakeEnabled = true,
        cameraMode = DjiCameraMode.VIDEO, videoResolution = 16, videoFps = 6)
    private fun render(height: Int, state: DjiWidgetViewState = DjiWidgetViewState(camera), interactive: Boolean = true): View {
        val config = ViewConfig(Pair(60, if (height >= 280) 60 else if (height >= 170) 30 else if (height >= 130) 20 else 15), Pair(480, height), 32)
        val view = DjiWidgetRenderer.cameraControl(context, config, state, interactive).apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 480, height)
        return view
    }
    private fun save(view: View, name: String) {
        val width = view.width; val height = view.height
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/reports/settings-ui/cam-control").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun refreshAndConnectUseTheExpectedTargetsAtAllGallerySizes() {
        val sizes = listOf(
            Triple("full", 480 to 420, 60 to 60),
            Triple("large", 480 to 189, 60 to 30),
            Triple("medium", 480 to 154, 60 to 20),
            Triple("small", 480 to 113, 60 to 15),
            Triple("half", 238 to 113, 30 to 15),
        )
        val offline = camera.copy(connection = CameraConnectionState.DISCONNECTED)
        val photo = camera.copy(cameraMode = DjiCameraMode.PHOTO)
        val states = linkedMapOf(
            "ready" to DjiWidgetViewState(camera),
            "refresh" to DjiWidgetViewState(photo),
            "switching" to DjiWidgetViewState(photo, refreshingVideoSettings = true),
            "offline" to DjiWidgetViewState(offline),
            "searching" to DjiWidgetViewState(offline, connectPrompts = setOf(ConnectPrompt.CONTROL)),
        )
        for ((name, size, grid) in sizes) {
            val config = ViewConfig(grid, size, 32)
            val panelId = if (name == "full") R.id.widgetControlFullStatus else R.id.widgetControlStatus
            val messageId = if (name == "full") R.id.widgetControlTopMessageText else R.id.widgetControlMessageText
            val remote = DjiWidgetRenderer.cameraControl(context, config, states.getValue("ready"))
            val view = remote.apply(context, FrameLayout(context))
            for ((scenario, state) in states) {
                DjiWidgetRenderer.cameraControl(context, config, state).reapply(context, view)
                val (width, height) = size
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                if (scenario in setOf("offline", "searching")) {
                    val expected = if (scenario == "searching") R.string.widget_searching else R.string.widget_connect
                    assertEquals(context.getString(expected), view.findViewById<TextView>(messageId).text.toString())
                } else {
                    val panel = view.findViewById<View>(panelId)
                    assertEquals(scenario == "refresh", panel.hasOnClickListeners())
                    assertTrue(panel.height > 0)
                }
                save(view, "review-$name-$scenario")
                val captionHeight = context.resources.getDimensionPixelSize(R.dimen.widget_field_caption_height)
                val fieldConfig = config.copy(viewSize = width to height - captionHeight)
                val field = DjiWidgetRenderer.captionedField(
                    context, R.string.dji_power, true,
                    DjiWidgetRenderer.cameraControl(context, fieldConfig, state, interactive = false),
                ).apply(context, FrameLayout(context))
                field.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                field.layout(0, 0, width, height)
                if (scenario !in setOf("offline", "searching")) {
                    val panel = field.findViewById<ViewGroup>(panelId)
                    val videoLine = panel.getChildAt(panel.childCount - 1)
                    assertTrue("$name/$scenario readout fits below the native caption", videoLine.top >= 0 && videoLine.bottom <= panel.height)
                }
                save(field, "field-$name-$scenario")
            }
        }
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
                val gap = context.resources.getDimensionPixelSize(R.dimen.widget_combined_gap)
                val photoGap = (photo.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin
                assertEquals(gap, video.top - view.findViewById<View>(R.id.widgetControlFullUtility).bottom)
                assertEquals(gap, power.top - photo.bottom)
                assertEquals(photoGap, photo.top - video.bottom)
                assertTrue(abs(4 * video.height + 2 * gap + photoGap + view.findViewById<View>(R.id.widgetControlFullStatus).height - (height - view.paddingTop - view.paddingBottom)) <= 3)
                assertEquals(DjiTheme.widgetPalette(context).textPrimary, view.findViewById<TextView>(R.id.widgetControlFullBattery).currentTextColor)
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
            assertEquals(DjiTheme.widgetPalette(context).textPrimary, low.findViewById<TextView>(R.id.widgetControlFullBattery).currentTextColor)
            save(low, "$theme-xl-low-battery")
            val recording = render(420, DjiWidgetViewState(camera.copy(recording = true, highlightFeedback = "HIGHLIGHT")))
            assertEquals(View.VISIBLE, recording.findViewById<View>(R.id.widgetControlVideoHighlight).visibility)
            assertEquals(View.VISIBLE, recording.findViewById<View>(R.id.widgetControlVideoRecording).visibility)
            assertEquals("14:45", recording.findViewById<TextView>(R.id.widgetControlVideoTimer).text.toString())
            assertFalse(recording.findViewById<View>(R.id.widgetControlPhoto).isEnabled)
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
            assertEquals(DjiTheme.widgetPalette(context).textPrimary, view.findViewById<TextView>(R.id.widgetControlBattery).currentTextColor)
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
    @Test fun feedbackAndConfirmationOccupyTheirIntendedSlotsAndRestoreOnReapply() {
        val fullConfig = ViewConfig(Pair(60,60), Pair(480,420), 32)
        val normal = render(420)
        val power = normal.findViewById<View>(R.id.widgetControlFullSleep)
        val powerTop = power.top; val powerHeight = power.height
        DjiWidgetRenderer.cameraControl(context, fullConfig, DjiWidgetViewState(camera, sleepConfirmation = true)).reapply(context, normal)
        normal.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY)); normal.layout(0,0,480,420)
        val confirm = normal.findViewById<View>(R.id.widgetControlTopConfirm)
        assertEquals(powerTop, confirm.top); assertEquals(powerHeight, confirm.height)
        assertEquals(View.VISIBLE, normal.findViewById<View>(R.id.widgetControlFullUtility).visibility)
        assertEquals(android.view.Gravity.CENTER, normal.findViewById<TextView>(R.id.widgetControlTopConfirmText).gravity)
        save(normal, "qa2-confirmation-power-row")
        DjiWidgetRenderer.cameraControl(context, fullConfig, DjiWidgetViewState(camera, controlFeedback = "Prevent standby · On")).reapply(context, normal)
        assertEquals(View.VISIBLE, normal.findViewById<View>(R.id.widgetControlFeedback).visibility)
        assertEquals(View.GONE, normal.findViewById<View>(R.id.widgetControlMore).visibility)
        save(normal, "qa2-full-feedback")
        DjiWidgetRenderer.cameraControl(context, fullConfig, DjiWidgetViewState(camera)).reapply(context, normal)
        assertEquals(View.GONE, normal.findViewById<View>(R.id.widgetControlFeedback).visibility)
        assertEquals(View.VISIBLE, normal.findViewById<View>(R.id.widgetControlFullStatus).visibility)
        assertEquals(View.VISIBLE, normal.findViewById<View>(R.id.widgetControlMore).visibility)
        val compactConfig = ViewConfig(Pair(60,30), Pair(480,189),32)
        val compact = render(189)
        DjiWidgetRenderer.cameraControl(context, compactConfig, DjiWidgetViewState(camera, controlFeedback="Prevent standby · Off")).reapply(context, compact)
        assertEquals(View.VISIBLE, compact.findViewById<View>(R.id.widgetControlMessageContainer).visibility)
        assertEquals(View.GONE, compact.findViewById<View>(R.id.widgetControlMessageSettings).visibility)
        assertEquals(View.GONE, compact.findViewById<View>(R.id.widgetControlMain).visibility)
        save(compact, "qa2-compact-feedback")
        DjiWidgetRenderer.cameraControl(context, compactConfig, DjiWidgetViewState(camera)).reapply(context, compact)
        assertEquals(View.VISIBLE, compact.findViewById<View>(R.id.widgetControlMain).visibility)
        assertTrue(compact.findViewById<View>(R.id.widgetControlSettings).hasOnClickListeners())
    }

    @Test fun halfWidthCaptionMatchesMeasuredBellGeometryAndKeepsCornersTransparent() {
        val captionHeight = context.resources.getDimensionPixelSize(R.dimen.widget_field_caption_height)
        val config = ViewConfig(Pair(30,15), Pair(238,125-captionHeight),26)
        val field = DjiWidgetRenderer.captionedField(context,R.string.dji_status,true,
            DjiWidgetRenderer.video(context,config,DjiWidgetViewState(camera)))
            .apply(context,FrameLayout(context))
        field.measure(View.MeasureSpec.makeMeasureSpec(238,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(125,View.MeasureSpec.EXACTLY));field.layout(0,0,238,125)
        val button = field.findViewById<View>(R.id.widgetVideoPrimary)
        val bounds=android.graphics.Rect(0,0,button.width,button.height)
        (field as ViewGroup).offsetDescendantRectToMyCoords(button,bounds)
        assertEquals(45,bounds.top);assertEquals(72,button.height)
        assertTrue(field.clipToOutline)
        val bitmap=Bitmap.createBitmap(238,125,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA);field.draw(Canvas(bitmap))
        for ((x,y) in listOf(0 to 0,237 to 0,0 to 124,237 to 124))
            assertEquals("Field must not paint outside its rounded corners",android.graphics.Color.MAGENTA,bitmap.getPixel(x,y))
        bitmap.recycle()
        save(field,"bell-measured-half-width")
    }

    @Test fun disconnectedFullControlHasOneCenteredConnectAndDisabledPowerThenRestores() {
        val config=ViewConfig(Pair(60,60),Pair(480,420),32,ViewConfig.Alignment.LEFT)
        val view=render(420)
        DjiWidgetRenderer.cameraControl(context,config,DjiWidgetViewState(camera.copy(connection=CameraConnectionState.DISCONNECTED))).reapply(context,view)
        val message=view.findViewById<TextView>(R.id.widgetControlTopMessageText)
        assertEquals(context.getString(R.string.widget_connect),message.text.toString())
        assertEquals(android.view.Gravity.CENTER,message.gravity)
        assertEquals(View.GONE,view.findViewById<View>(R.id.widgetControlFullOfflineHint).visibility)
        assertEquals("",view.findViewById<TextView>(R.id.widgetControlFullOfflineHint).text.toString())
        val power=view.findViewById<View>(R.id.widgetControlFullSleep)
        assertFalse(power.isEnabled);assertFalse(power.hasOnClickListeners())
        save(view,"disconnected-centered-connect")
        DjiWidgetRenderer.cameraControl(context,config,DjiWidgetViewState(camera)).reapply(context,view)
        assertTrue(power.isEnabled);assertTrue(power.hasOnClickListeners())
        save(view,"reconnected-power-restored")
    }

    @Test fun captionsAndButtonBoundsMatchAcrossSingleAndCombinedFields() {
        val config = ViewConfig(Pair(60,30), Pair(480,144),32)
        val state = DjiWidgetViewState(camera)
        val widgets = listOf("video" to DjiWidgetRenderer.video(context,config,state),
            "photo" to DjiWidgetRenderer.photo(context,config,state),
            "combined" to DjiWidgetRenderer.videoPhoto(context,config,state))
        var captionBounds: List<Int>? = null
        var buttonBounds: List<Int>? = null
        for ((name, body) in widgets) {
            val field = DjiWidgetRenderer.captionedField(context, when(name) { "photo" -> R.string.dji_photo; "combined" -> R.string.dji_video_photo; else -> R.string.dji_status }, true, body)
                .apply(context, FrameLayout(context))
            field.measure(View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(189,View.MeasureSpec.EXACTLY));field.layout(0,0,480,189)
            val caption = field.findViewById<TextView>(R.id.widgetFieldCaption)
            val bounds = listOf(caption.top,caption.height,caption.paddingLeft)
            if (captionBounds == null) captionBounds = bounds else assertEquals(captionBounds,bounds)
            val id = if (name=="photo") R.id.widgetPhotoPrimary else R.id.widgetVideoPrimary
            val button = field.findViewById<View>(id)
            val pos = IntArray(2);button.getLocationInWindow(pos)
            val dimensions = listOf(pos[1],button.height)
            if(buttonBounds==null) buttonBounds=dimensions else assertEquals(buttonBounds,dimensions)
            save(field,"qa2-caption-$name")
        }
    }

    @Test fun halfWidthShowsOnlyStatusAtShortAndTallHeightsAndRestoresFullWidthControls() {
        for (theme in listOf("light", "dark")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE,theme).commit()
            for (height in listOf(113,189,420)) {
                val config = ViewConfig(Pair(30,if (height>=280) 60 else 15),Pair(238,height),26)
                val state = DjiWidgetViewState(camera.copy(batteryPercent=15))
                val remote = DjiWidgetRenderer.cameraControl(context,config,state)
                val view = remote.apply(context,FrameLayout(context))
                view.measure(View.MeasureSpec.makeMeasureSpec(238,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
                view.layout(0,0,238,height)
                assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widgetControlMain).visibility)
                for (id in listOf(R.id.widgetControlKeepAwake,R.id.widgetControlSettings,R.id.widgetControlMessageContainer,R.id.widgetControlFullShell)) {
                    assertEquals(View.GONE,view.findViewById<View>(id).visibility)
                }
                val battery=view.findViewById<TextView>(R.id.widgetControlBattery)
                assertEquals("15%",battery.text.toString())
                assertEquals(DjiTheme.widgetPalette(context).textPrimary,battery.currentTextColor)
                assertTrue(view.findViewById<TextView>(R.id.widgetControlStorage).text.toString().contains("14.5"))
                save(view,"$theme-half-$height-status")
                DjiWidgetRenderer.cameraControl(context,config,
                    state.copy(camera=camera.copy(connection=CameraConnectionState.DISCONNECTED))).reapply(context,view)
                assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widgetControlMessageContainer).visibility)
                assertEquals(View.GONE, view.findViewById<View>(R.id.widgetControlMessageSettings).visibility)
                assertEquals(context.getString(R.string.widget_connect), view.findViewById<TextView>(R.id.widgetControlMessageText).text.toString())
                assertTrue(view.findViewById<View>(R.id.widgetControlMessage).hasOnClickListeners())
                save(view, "$theme-half-$height-offline")
                val fullConfig=ViewConfig(Pair(60,15),Pair(480,113),26)
                DjiWidgetRenderer.cameraControl(context,fullConfig,state).reapply(context,view)
                assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widgetControlKeepAwake).visibility)
                assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widgetControlSettings).visibility)
            }
        }
    }
}
