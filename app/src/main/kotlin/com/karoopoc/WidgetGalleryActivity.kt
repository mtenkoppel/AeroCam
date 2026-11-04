package com.karoopoc

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.PhotoCaptureState
import com.karoopoc.widget.DjiWidgetRenderer
import com.karoopoc.widget.DjiWidgetViewState
import io.hammerhead.karooext.models.ViewConfig

class WidgetGalleryActivity : Activity() {
    private lateinit var videoPreview: FrameLayout
    private lateinit var combinedPreview: FrameLayout
    private lateinit var photoPreview: FrameLayout
    private lateinit var controlPreview: FrameLayout
    private lateinit var palette: DjiPalette
    private var selectedSize = PreviewSize.LARGE
    private var selectedScenario = PreviewScenario.READY

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DjiTheme.activityContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        palette = DjiTheme.palette(this)
        setContentView(createGallery())
        renderPreviews()
    }

    private fun createGallery(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 18, 12, 30)
            setBackgroundColor(palette.galleryBackground)
            addView(label(getString(R.string.gallery_title), 24f))
            addView(label(getString(R.string.gallery_size), 13f))
            addView(sizeSpinner())
            addView(label(getString(R.string.gallery_state), 13f))
            addView(scenarioSpinner())

            videoPreview = addPreview(getString(R.string.gallery_video))
            combinedPreview = addPreview(getString(R.string.dji_video_photo))
            photoPreview = addPreview(getString(R.string.gallery_photo))
            controlPreview = addPreview(getString(R.string.gallery_cam_control))
        }
        return ScrollView(this).apply {
            setBackgroundColor(palette.galleryBackground)
            addView(content)
        }
    }

    private fun LinearLayout.addPreview(title: String): FrameLayout {
        addView(label(title, 15f).apply { setPadding(0, 22, 0, 6) })
        val frame = FrameLayout(this@WidgetGalleryActivity).apply {
            setBackgroundColor(palette.previewBackground)
        }
        addView(frame, previewLayoutParams())
        return frame
    }

    private fun sizeSpinner(): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@WidgetGalleryActivity,
            android.R.layout.simple_spinner_dropdown_item,
            PreviewSize.entries.map { getString(it.labelRes) },
        )
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSize = PreviewSize.entries[position]
                renderPreviews()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun scenarioSpinner(): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@WidgetGalleryActivity,
            android.R.layout.simple_spinner_dropdown_item,
            PreviewScenario.entries.map { getString(it.labelRes) },
        )
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedScenario = PreviewScenario.entries[position]
                renderPreviews()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderPreviews() {
        if (!::videoPreview.isInitialized) return
        val config = selectedSize.config
        val state = if (selectedScenario == PreviewScenario.BRIEF_FEEDBACK) {
            selectedScenario.viewState.copy(
                camera = selectedScenario.viewState.camera.copy(
                    highlightFeedback = getString(R.string.widget_speed_feedback),
                ),
                controlFeedback = getString(R.string.widget_keep_awake_on),
            )
        } else {
            selectedScenario.viewState
        }
        listOf(videoPreview, photoPreview, combinedPreview, controlPreview).forEach { preview ->
            preview.layoutParams = previewLayoutParams()
            preview.removeAllViews()
        }
        applyPreview(videoPreview, DjiWidgetRenderer.video(this, config, state, interactive = false))
        applyPreview(combinedPreview, DjiWidgetRenderer.videoPhoto(this, config, state, interactive = false))
        applyPreview(photoPreview, DjiWidgetRenderer.photo(this, config, state, interactive = false))
        applyPreview(controlPreview, DjiWidgetRenderer.cameraControl(this, config, state, interactive = false))
    }

    private fun applyPreview(parent: FrameLayout, remoteViews: android.widget.RemoteViews) {
        val view = remoteViews.apply(this, parent)
        parent.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun previewLayoutParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            selectedSize.config.viewSize.first,
            selectedSize.config.viewSize.second,
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    private fun label(value: String, size: Float): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(palette.textPrimary)
    }

    private enum class PreviewSize(
        val labelRes: Int,
        val config: ViewConfig,
    ) {
        LARGE(R.string.gallery_size_large, ViewConfig(Pair(60, 30), Pair(480, 189), 40)),
        MEDIUM(R.string.gallery_size_medium, ViewConfig(Pair(60, 20), Pair(480, 154), 32)),
        SMALL(R.string.gallery_size_small, ViewConfig(Pair(60, 15), Pair(480, 113), 26)),
        SMALL_HALF(R.string.gallery_size_small_half, ViewConfig(Pair(30, 15), Pair(238, 113), 24)),
        FULL(R.string.gallery_size_full, ViewConfig(Pair(60, 60), Pair(480, 420), 40)),
    }

    private enum class PreviewScenario(
        val labelRes: Int,
        val viewState: DjiWidgetViewState,
    ) {
        READY(R.string.gallery_state_ready, viewState()),
        OFFLINE(
            R.string.gallery_state_offline,
            viewState(connection = CameraConnectionState.DISCONNECTED),
        ),
        RECONNECTING(
            R.string.gallery_state_reconnecting,
            viewState(connection = CameraConnectionState.SEARCHING),
        ),
        RECORDING(
            R.string.gallery_state_recording,
            viewState(recording = true),
        ),
        BRIEF_FEEDBACK(
            R.string.gallery_state_feedback,
            viewState(
                recording = true,
                photoState = PhotoCaptureState.COMPLETE,
                highlightFeedback = null,
                controlFeedback = null,
            ),
        ),
        KEEP_AWAKE(
            R.string.gallery_state_keep_awake,
            viewState(keepAwake = true),
        ),
        SLEEP_CONFIRMATION(
            R.string.gallery_state_sleep_confirmation,
            viewState(sleepConfirmation = true),
        ),
    }

    companion object {
        private fun viewState(
            connection: CameraConnectionState = CameraConnectionState.CONNECTED,
            recording: Boolean = false,
            keepAwake: Boolean = false,
            photoState: PhotoCaptureState = PhotoCaptureState.IDLE,
            highlightFeedback: String? = null,
            controlFeedback: String? = null,
            sleepConfirmation: Boolean = false,
        ): DjiWidgetViewState {
            return DjiWidgetViewState(
                camera = DjiCameraState(
                    configured = true,
                    connection = connection,
                    batteryPercent = 60,
                    keepAwakeEnabled = keepAwake,
                    keepAwakeActive = keepAwake,
                    recording = recording,
                    recordingDurationSeconds = 14 * 60 + 45,
                    remainingCapacityMb = 14_848,
                    remainingRecordingSeconds = 7_200,
                    videoResolution = 16,
                    videoFps = 3,
                    highlightCount = 4,
                    highlightFeedback = highlightFeedback,
                    photoState = photoState,
                ),
                controlFeedback = controlFeedback,
                sleepConfirmation = sleepConfirmation,
            )
        }
    }
}
