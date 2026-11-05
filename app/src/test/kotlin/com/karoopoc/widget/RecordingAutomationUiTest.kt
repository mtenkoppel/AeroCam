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
        assertTrue("Text exceeds height: ${text.text}, line=${text.layout.getLineBottom(0)}, height=${text.height}, padding=${text.compoundPaddingTop + text.compoundPaddingBottom}", text.layout.getLineBottom(0) <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom + 1)
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
                save(view,"$theme-video-$index-$mode")
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
    @Test fun captureDesignQaWithExplicitAlignmentAndActualFieldTitles() {
        val sizes = listOf(
            "half" to ViewConfig(Pair(30,15),Pair(238,113),24,ViewConfig.Alignment.CENTER),
            "small" to ViewConfig(Pair(60,15),Pair(480,113),26,ViewConfig.Alignment.CENTER),
            "medium" to ViewConfig(Pair(60,20),Pair(480,154),32,ViewConfig.Alignment.CENTER),
            "large" to ViewConfig(Pair(60,30),Pair(480,189),40,ViewConfig.Alignment.CENTER),
            "full" to ViewConfig(Pair(60,60),Pair(480,420),40,ViewConfig.Alignment.CENTER),
        )
        val dir = File("build/reports/settings-ui/recording-design-qa").apply { mkdirs() }
        fun capture(name: String, c: ViewConfig, state: DjiWidgetViewState, control: Boolean = false): Bitmap {
            val captionHeight=context.resources.getDimensionPixelSize(R.dimen.widget_field_caption_height)
            val bodyConfig=c.copy(viewSize=Pair(c.viewSize.first,c.viewSize.second-captionHeight))
            val body=if(control) DjiWidgetRenderer.cameraControl(context,bodyConfig,state,false)
                else DjiWidgetRenderer.video(context,bodyConfig,state,false)
            val caption=if(control) R.string.gallery_cam_control else R.string.widget_video
            val view=DjiWidgetRenderer.captionedField(context,caption,true,body).apply(context,FrameLayout(context))
            layout(view,c)
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            return bitmap
        }
        fun board(name: String, headings: List<String>, rows: List<Pair<String,List<Bitmap>>>) {
            val width=40+headings.size*500
            val height=80+rows.sumOf { it.second.maxOf { image -> image.height }+44 }
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap);canvas.drawColor(android.graphics.Color.rgb(232,236,240))
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=android.graphics.Color.rgb(25,30,35);textSize=21f
            }
            headings.forEachIndexed { i,h -> canvas.drawText(h,20f+i*500,32f,paint) }
            var y=60
            rows.forEach { (label,images) ->
                canvas.drawText(label,20f,y.toFloat(),paint)
                images.forEachIndexed { i,image -> canvas.drawBitmap(image,20f+i*500,(y+10).toFloat(),null) }
                y+=images.maxOf { it.height }+44
            }
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
            rows.flatMap { it.second }.forEach { it.recycle() }
        }
        for(theme in listOf("dark","light")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE,theme).commit()
            val scenarios=listOf(RecordingAutomation.NONE,RecordingAutomation.AUTO_START,RecordingAutomation.AUTO_START_STOP)
            val normal=recording.copy(camera=recording.camera.copy(recordingDurationSeconds=84,batteryPercent=95))
            val rows=sizes.map { (name,c) -> name to scenarios.map { mode ->
                capture("$theme-center-$name-$mode",c,normal.copy(recordingAutomation=mode))
            }}
            board("$theme-centered-sizes",listOf("Manual · centered","AUTO START · centered","AUTO START + STOP · centered"),rows)
            val both=normal.copy(recordingAutomation=RecordingAutomation.AUTO_START_STOP)
            val checks=listOf(
                "Long clip · 62:34" to both.copy(camera=both.camera.copy(recordingDurationSeconds=3754)),
                "Highlight feedback" to both.copy(camera=both.camera.copy(highlightFeedback="HIGHLIGHT")),
                "Stop awaiting response" to both.copy(videoAwaitingResponse="pending"),
            )
            val checkRows=sizes.map { (name,c) -> name to checks.mapIndexed { i,(_,state) ->
                capture("$theme-center-$name-check-$i",c,state)
            }}
            board("$theme-recording-states",checks.map { it.first },checkRows)
            val full=sizes.last().second
            board("$theme-cam-control",listOf("Manual · centered","AUTO START · centered","AUTO START + STOP · centered"),
                listOf("Full Cam Control" to scenarios.map { mode -> capture("$theme-center-control-$mode",full,normal.copy(recordingAutomation=mode),true) }))
            val alignments=listOf(ViewConfig.Alignment.LEFT,ViewConfig.Alignment.CENTER,ViewConfig.Alignment.RIGHT)
            val alignRows=listOf("small" to sizes[1].second,"large" to sizes[3].second,"control" to full).map { (name,c) ->
                name to alignments.map { alignment -> capture("$theme-alignment-$name-$alignment",c.copy(alignment=alignment),both,name=="control") }
            }
            board("$theme-alignment-bug",listOf("LEFT requested","CENTER requested","RIGHT requested"),alignRows)
        }
    }

}
