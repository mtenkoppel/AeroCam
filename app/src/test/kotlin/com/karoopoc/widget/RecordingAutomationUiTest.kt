package com.karoopoc.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
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
class RecordingAutomationUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val recording = DjiWidgetViewState(DjiCameraState(configured = true,
        connection = CameraConnectionState.CONNECTED, recording = true,
        recordingDurationSeconds = 3754, remainingCapacityMb = 14848))
    private val configs = listOf(
        ViewConfig(Pair(30,15), Pair(238,68),24), // smallest half field with caption space removed
        ViewConfig(Pair(60,15), Pair(480,68),26),
        ViewConfig(Pair(60,20), Pair(480,109),32),
        ViewConfig(Pair(60,30), Pair(480,144),40),
        ViewConfig(Pair(60,60), Pair(480,375),40),
    )
    private fun layout(view: View, c: ViewConfig) {
        view.measure(View.MeasureSpec.makeMeasureSpec(c.viewSize.first, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(c.viewSize.second, View.MeasureSpec.EXACTLY))
        view.layout(0,0,c.viewSize.first,c.viewSize.second)
    }
    private fun fits(view: View, id: Int) {
        val text = view.findViewById<TextView>(id)
        assertEquals(View.VISIBLE, text.visibility)
        assertTrue("Text exceeds width: ${text.text}", text.paint.measureText(text.text.toString()) <= text.width - text.paddingLeft - text.paddingRight + 1)
        assertTrue("Text exceeds height: ${text.text}", text.layout.getLineBottom(0) <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom + 1)
        val parent = text.parent as View
        assertTrue("Text outside parent: ${text.text}", text.top >= 0 && text.bottom <= parent.height)
    }
    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/reports/settings-ui/recording-automation").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun labelsFitEverySizeAndKeepExistingHighlightControls() {
        for (theme in listOf("light","dark")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE,theme).commit()
            for ((index,c) in configs.withIndex()) for (mode in RecordingAutomation.entries) {
                val state = recording.copy(recordingAutomation = mode)
                val remote = DjiWidgetRenderer.video(context,c,state,false)
                val view = remote.apply(context,FrameLayout(context)); layout(view,c)
                assertEquals(View.GONE,view.findViewById<View>(R.id.widgetVideoMeta).visibility)
                assertEquals(if(index==0) View.GONE else View.VISIBLE,view.findViewById<View>(R.id.widgetVideoHighlight).visibility)
                if(mode!=RecordingAutomation.NONE) {
                    fits(view,R.id.widgetVideoTimer); fits(view,R.id.widgetVideoAutomation)
                    assertEquals(if(mode==RecordingAutomation.AUTO_START) "AUTO START" else "AUTO START + STOP",
                        view.findViewById<TextView>(R.id.widgetVideoAutomation).text.toString())
                } else assertEquals(View.GONE,view.findViewById<View>(R.id.widgetVideoAutomation).visibility)
                save(view,"$theme-video-$index-$mode")
                val combined=DjiWidgetRenderer.videoPhoto(context,c,state,false).apply(context,FrameLayout(context))
                assertEquals(view.findViewById<View>(R.id.widgetVideoAutomation).visibility,
                    combined.findViewById<View>(R.id.widgetVideoAutomation).visibility)
                // Expiring feedback must not remove the automation status.
                val feedback=DjiWidgetRenderer.video(context,c,state.copy(camera=state.camera.copy(highlightFeedback="HIGHLIGHT")),false)
                feedback.reapply(context,view); layout(view,c)
                if(mode!=RecordingAutomation.NONE) fits(view,R.id.widgetVideoAutomation)
                remote.reapply(context,view); layout(view,c)
                if(mode!=RecordingAutomation.NONE) fits(view,R.id.widgetVideoTimer)
                DjiWidgetRenderer.video(context,c,recording,false).reapply(context,view)
                assertEquals(View.GONE,view.findViewById<View>(R.id.widgetVideoAutomation).visibility)
            }
        }
    }
    @Test fun controlLabelFitsWithFeedbackAndClearsForIdleOfflineAndPendingStates() {
        val c=configs.last()
        for(mode in listOf(RecordingAutomation.AUTO_START,RecordingAutomation.AUTO_START_STOP)) {
            val state=recording.copy(recordingAutomation=mode)
            val view=DjiWidgetRenderer.cameraControl(context,c,state,false).apply(context,FrameLayout(context))
            layout(view,c); fits(view,R.id.widgetControlVideoTimer); fits(view,R.id.widgetControlVideoAutomation)
            save(view,"control-$mode")
            val feedback=state.copy(camera=state.camera.copy(highlightFeedback="HIGHLIGHT"))
            DjiWidgetRenderer.cameraControl(context,c,feedback,false).reapply(context,view)
            layout(view,c); fits(view,R.id.widgetControlVideoAutomation)
            assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widgetControlVideoFeedback).visibility)
            save(view,"control-feedback-$mode")
            for(inactive in listOf(state.copy(camera=state.camera.copy(recording=false)),
                state.copy(camera=state.camera.copy(connection=CameraConnectionState.DISCONNECTED)),
                state.copy(videoAwaitingResponse="pending"),recording)) {
                DjiWidgetRenderer.cameraControl(context,c,inactive,false).reapply(context,view)
                assertFalse(view.findViewById<View>(R.id.widgetControlVideoAutomation).isShown)
            }
        }
    }
}
