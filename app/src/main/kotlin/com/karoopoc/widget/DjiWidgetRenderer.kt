package com.karoopoc.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.karoopoc.DjiPalette
import com.karoopoc.DjiTheme
import com.karoopoc.MainActivity
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraMode
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.PhotoCaptureState
import io.hammerhead.karooext.models.ViewConfig

data class DjiWidgetViewState(
    val camera: DjiCameraState,
    val controlFeedback: String? = null,
    val sleepConfirmation: Boolean = false,
    val connectPrompts: Set<ConnectPrompt> = emptySet(),
    val photoAwaitingResponse: String? = null,
    val videoAwaitingResponse: String? = null,
    val refreshingVideoSettings: Boolean = false,
)

enum class DjiWidgetSize {
    SMALL_HALF,
    SMALL,
    MEDIUM,
    LARGE,
    FULL,
}

object DjiWidgetLayout {
    fun sizeFor(config: ViewConfig): DjiWidgetSize = sizeFor(
        width = config.viewSize.first,
        height = config.viewSize.second,
        columns = config.gridSize.first,
        rows = config.gridSize.second,
    )

    fun sizeFor(width: Int, height: Int, columns: Int, rows: Int): DjiWidgetSize {
        return when {
            rows >= 45 || height >= 280 -> DjiWidgetSize.FULL
            columns <= 30 || width < 360 -> DjiWidgetSize.SMALL_HALF
            rows >= 25 || height >= 170 -> DjiWidgetSize.LARGE
            rows >= 20 || height >= 130 -> DjiWidgetSize.MEDIUM
            else -> DjiWidgetSize.SMALL
        }
    }
}

object DjiWidgetRenderer {
    fun captionedField(context: Context, caption: Int, showCaption: Boolean, body: RemoteViews): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dji_field).apply {
            body.applyWidgetFrameForCaption(showCaption)
            setTextViewText(R.id.widgetFieldCaption, context.getString(caption).uppercase())
            setViewVisibility(R.id.widgetFieldCaption, if (showCaption) View.VISIBLE else View.GONE)
            removeAllViews(R.id.widgetFieldBody)
            addView(R.id.widgetFieldBody, body)
        }

    /** Two actions when idle, one camera connection or recording control when shared. */
    fun videoPhoto(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews {
        if (viewState.camera.recording || viewState.camera.connection != CameraConnectionState.CONNECTED) {
            return video(context, config, viewState, interactive)
        }

        val gap = context.resources.getDimensionPixelSize(R.dimen.widget_combined_gap)
        val slotConfig = config.copy(
            gridSize = Pair((config.gridSize.first / 2).coerceAtLeast(1), config.gridSize.second),
            viewSize = Pair(
                ((config.viewSize.first - FIELD_HORIZONTAL_INSET - gap) / 2).coerceAtLeast(1),
                (config.viewSize.second - FIELD_VERTICAL_INSET).coerceAtLeast(1),
            ),
        )
        fun RemoteViews.withoutOuterFrame(rootId: Int) = apply {
            // The combined container owns the one outer frame. Keep child surfaces,
            // selectors, PendingIntents and all state-dependent rendering intact.
            setViewPadding(rootId, 0, 0, 0, 0)
            setInt(rootId, "setBackgroundColor", Color.TRANSPARENT)
        }
        return RemoteViews(context.packageName, R.layout.widget_dji_video_photo).apply {
            setInt(R.id.widgetVideoPhotoRoot, "setBackgroundResource", R.drawable.widget_root_dark)
            applyWidgetFrame(R.id.widgetVideoPhotoRoot)
            removeAllViews(R.id.widgetCombinedVideo)
            removeAllViews(R.id.widgetCombinedPhoto)
            addView(R.id.widgetCombinedVideo, video(context, slotConfig, viewState, interactive).withoutOuterFrame(R.id.widgetVideoRoot))
            addView(R.id.widgetCombinedPhoto, photo(context, slotConfig, viewState, interactive).withoutOuterFrame(R.id.widgetPhotoRoot))
        }
    }

    fun video(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_dji_video).apply {
        applyWidgetFrame(R.id.widgetVideoRoot)
        renderVideoButton(context, config, viewState, config.viewSize.second - FIELD_VERTICAL_INSET, interactive)
    }

    fun photo(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_dji_photo).apply {
        applyWidgetFrame(R.id.widgetPhotoRoot)
        renderPhotoButton(context, config, viewState, interactive)
    }

    private fun RemoteViews.renderVideoButton(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        buttonHeight: Int,
        interactive: Boolean,
        hasConnectionButton: Boolean = false,
    ) {
        val state = viewState.camera
        val palette = DjiTheme.widgetPalette(context)
        val size = DjiWidgetLayout.sizeFor(config)
        val connected = state.connection == CameraConnectionState.CONNECTED
        val recording = connected && state.recording
        val pending = connected && viewState.videoAwaitingResponse != null
        val showConnect = !connected && !hasConnectionButton
        val showHighlight = recording && (hasConnectionButton || size != DjiWidgetSize.SMALL_HALF)

        centerWidgetText(R.id.widgetVideoTimer, R.id.widgetVideoMeta, R.id.widgetVideoStatus)
        applyIconPadding(context, R.dimen.widget_icon_primary_padding, R.id.widgetVideoIcon)
        applyIconPadding(context, R.dimen.widget_icon_utility_padding, R.id.widgetVideoHighlightIcon)
        setTextColor(R.id.widgetVideoTimer, palette.textOnAccent)
        setTextColor(R.id.widgetVideoMeta, palette.textOnAccent)
        val automation = com.karoopoc.dji.DjiSettings.read(context)
        setImageViewResource(R.id.widgetVideoIcon, when {
            !connected -> R.drawable.ic_widget_video_disabled
            automation.autoHighlightsEnabled && automation.autoStartRecording -> R.drawable.ic_widget_video_auto
            else -> R.drawable.ic_widget_video
        })
        setViewVisibility(R.id.widgetVideoIdle, if (!recording && !pending && !showConnect) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widgetVideoRecording, if (recording && !pending) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widgetVideoSpinner, if (pending) View.VISIBLE else View.GONE)
        setViewVisibility(R.id.widgetVideoHighlight, if (showHighlight) View.VISIBLE else View.GONE)

        val idleLine = when {
            state.automationFeedback == "STOPPED" -> context.getString(R.string.automation_rec_stopped)
            else -> null
        }
        if (idleLine != null) {
            val preferred = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, context.resources.displayMetrics)
            val paint = Paint().apply { textSize = preferred; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            val available = (config.viewSize.first - FIELD_HORIZONTAL_INSET).coerceAtLeast(1)
            setTextViewTextSize(R.id.widgetVideoIdleMeta, TypedValue.COMPLEX_UNIT_PX,
                minOf(preferred, preferred * available / paint.measureText(idleLine)))
        }
        setTextViewText(R.id.widgetVideoIdleMeta, idleLine ?: "")
        setViewVisibility(R.id.widgetVideoIdleMeta, if (idleLine != null) View.VISIBLE else View.GONE)
        // Preserve the existing 26dp glyph; borrow its vertical padding for the subline in short slots.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val density = context.resources.displayMetrics.density
            val normalIcon = context.resources.getDimensionPixelSize(R.dimen.widget_icon_primary_size)
            val lineHeight = if (idleLine != null) (15 * context.resources.displayMetrics.scaledDensity).toInt() else 0
            val iconHeight = minOf(normalIcon, (buttonHeight - lineHeight - 4 * density).toInt()).coerceAtLeast(1)
            setViewLayoutHeight(R.id.widgetVideoIcon, iconHeight.toFloat(), android.util.TypedValue.COMPLEX_UNIT_PX)
            val sidePadding = context.resources.getDimensionPixelSize(R.dimen.widget_icon_primary_padding)
            val verticalPadding = minOf(sidePadding, ((iconHeight - 26 * density) / 2).toInt().coerceAtLeast(0))
            setViewPadding(R.id.widgetVideoIcon, sidePadding, verticalPadding, sidePadding, verticalPadding)
        }
        var markHighlight = false
        if (recording && !pending) {
            val resources = context.resources
            val highlightWidth = if (showHighlight) {
                resources.getDimensionPixelSize(R.dimen.widget_highlight_button_width) +
                    resources.getDimensionPixelSize(R.dimen.widget_combined_gap)
            } else 0
            val feedback = when {
                state.automationFeedback == "STARTED" -> context.getString(R.string.automation_rec_started)
                state.highlightFeedback != null -> "★ ${compactFeedback(context, state.highlightFeedback)}"
                else -> null
            }
            val autoStopLine = if (state.automaticRecordingId != 0L && automation.autoHighlightsEnabled &&
                automation.autoStartRecording && automation.autoStop) context.getString(R.string.automation_auto_rec) else null
            markHighlight = RecordingButtonLayout.render(
                this, resources, config.viewSize.first - FIELD_HORIZONTAL_INSET - highlightWidth, buttonHeight,
                formatDuration(state.recordingDurationSeconds), config.textSize, feedback,
                // Cam Control already shows available storage in its status area.
                metadata = autoStopLine,
            )
        }
        setInt(R.id.widgetVideoHighlight, "setBackgroundResource",
            if (markHighlight) R.drawable.widget_surface_active else R.drawable.widget_surface_selector)
        setOnClickPendingIntent(
            R.id.widgetVideoHighlight,
            if (interactive && showHighlight) actionIntent(context, DjiWidgetActionReceiver.ACTION_HIGHLIGHT, 102) else null,
        )
        setInt(R.id.widgetVideoPrimary, "setBackgroundResource", when {
            recording -> R.drawable.widget_surface_recording
            connected || showConnect && state.configured -> R.drawable.widget_surface_selector
            else -> R.drawable.widget_surface_dark
        })
        bindCameraAction(context, config, viewState, R.id.widgetVideoPrimary, R.id.widgetVideoStatus,
            interactive, connected, showConnect, DjiWidgetActionReceiver.ACTION_RECORD_TOGGLE, 101)
    }

    private fun RemoteViews.renderPhotoButton(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean,
        hasConnectionButton: Boolean = false,
    ) {
        val state = viewState.camera
        val connected = state.connection == CameraConnectionState.CONNECTED
        val available = connected && !state.recording
        val pending = available && (viewState.photoAwaitingResponse != null ||
            state.photoState == PhotoCaptureState.REQUESTED || state.photoState == PhotoCaptureState.CAPTURING)
        val completed = available && !pending && state.photoState == PhotoCaptureState.COMPLETE
        val showConnect = !connected && !hasConnectionButton

        applyIconPadding(context, R.dimen.widget_icon_primary_padding, R.id.widgetPhotoIcon)
        setViewVisibility(R.id.widgetPhotoIcon, if (pending || showConnect) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widgetPhotoSpinner, if (pending) View.VISIBLE else View.GONE)
        val automation = com.karoopoc.dji.DjiSettings.read(context)
        setImageViewResource(R.id.widgetPhotoIcon, when {
            !connected -> R.drawable.ic_widget_photo_disabled
            state.recording -> R.drawable.ic_widget_photo_muted
            completed -> R.drawable.ic_widget_photo_success
            automation.autoHighlightsEnabled && automation.mode == com.karoopoc.dji.AutomationMode.PHOTOS -> R.drawable.ic_widget_photo_auto
            else -> R.drawable.ic_widget_photo
        })
        setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", when {
            completed -> R.drawable.widget_surface_success
            available || showConnect && state.configured -> R.drawable.widget_surface_selector
            else -> R.drawable.widget_surface_dark
        })
        bindCameraAction(context, config, viewState, R.id.widgetPhotoPrimary, R.id.widgetPhotoStatus,
            interactive, available, showConnect, DjiWidgetActionReceiver.ACTION_PHOTO, 201)
    }

    private fun RemoteViews.bindCameraAction(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        button: Int,
        status: Int,
        interactive: Boolean,
        available: Boolean,
        showConnect: Boolean,
        action: String,
        requestCode: Int,
    ) {
        centerWidgetText(status)
        setViewVisibility(status, if (showConnect) View.VISIBLE else View.GONE)
        if (showConnect) {
            setTextViewText(status, connectionLabel(context, viewState))
            setTextViewTextSize(status, TypedValue.COMPLEX_UNIT_SP, statusTextSize(config, DjiWidgetLayout.sizeFor(config)))
            setTextColor(status, DjiTheme.widgetPalette(context).textPrimary)
        }
        setBoolean(button, "setEnabled", available || showConnect)
        setOnClickPendingIntent(button, when {
            !interactive -> null
            available -> actionIntent(context, action, requestCode)
            showConnect -> connectionIntent(context, viewState.camera.configured)
            else -> null
        })
    }

    fun cameraControl(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews {
        val state = viewState.camera
        val palette = DjiTheme.widgetPalette(context)
        // Cam Control uses a status-only view at half width, even in tall fields.
        // Keep the shared video/photo size policy unchanged.
        val narrow = config.gridSize.first <= 30 || config.viewSize.first < 360
        val size = if (narrow) DjiWidgetSize.SMALL_HALF else DjiWidgetLayout.sizeFor(config)
        val connected = state.connection == CameraConnectionState.CONNECTED
        val fullRowSpace = config.viewSize.second - FIELD_VERTICAL_INSET -
            3 * context.resources.getDimensionPixelSize(R.dimen.widget_combined_gap)
        val nominalRowHeight = (fullRowSpace / 4).coerceAtLeast(0)
        val toolbarMinimum = if (size == DjiWidgetSize.FULL && connected && viewState.controlFeedback == null) {
            fullControlStatusHeight(context)
        } else 0
        val fullButtonHeight = (fullRowSpace - maxOf(nominalRowHeight, toolbarMinimum)) / 3

        return RemoteViews(context.packageName, R.layout.widget_dji_control).apply {
            setInt(R.id.widgetControlRoot, "setBackgroundResource", R.drawable.widget_root_dark)
            applyWidgetFrame(R.id.widgetControlRoot)
            centerWidgetText(
                R.id.widgetControlMessageText, R.id.widgetControlTopMessageText,
                R.id.widgetControlTopConfirmText, R.id.widgetControlFullOfflineHint)
            setViewVisibility(R.id.widgetControlFeedback, View.GONE)
            setViewVisibility(R.id.widgetControlMore, View.VISIBLE)
            applyIconPadding(
                context,
                R.dimen.widget_icon_utility_padding,
                R.id.widgetControlKeepAwakeIcon,
                R.id.widgetControlSettingsIcon,
                R.id.widgetControlMessageSettingsIcon,
                R.id.widgetControlConfirmYesIcon,
                R.id.widgetControlConfirmNoIcon,
                R.id.widgetControlFullKeepAwakeIcon,
                R.id.widgetControlMoreIcon,
                R.id.widgetControlFullSleepIcon,
                R.id.widgetControlOfflineMoreIcon,
                R.id.widgetControlTopConfirmYesIcon,
                R.id.widgetControlTopConfirmNoIcon,
            )
            setViewVisibility(R.id.widgetControlMain, View.GONE)
            setViewVisibility(R.id.widgetControlKeepAwake, View.VISIBLE)
            setViewVisibility(R.id.widgetControlSettings, View.VISIBLE)
            setViewVisibility(R.id.widgetControlMessageContainer, View.GONE)
            setViewVisibility(R.id.widgetControlMessageSettings, View.VISIBLE)
            setViewVisibility(R.id.widgetControlConfirm, View.GONE)
            setViewVisibility(R.id.widgetControlFullShell, View.GONE)
            setViewVisibility(R.id.widgetControlFullUtility, View.GONE)
            setViewVisibility(R.id.widgetControlFullOffline, View.GONE)
            setViewVisibility(R.id.widgetControlTopConfirm, View.GONE)
            setViewVisibility(R.id.widgetControlFullStatus, View.GONE)
            setViewVisibility(R.id.widgetControlFull, View.GONE)
            setViewVisibility(R.id.widgetControlPhoto, View.GONE)
            setViewVisibility(R.id.widgetControlFullSleep, View.GONE)
            setOnClickPendingIntent(R.id.widgetControlFullSleep, null)
            setOnClickPendingIntent(R.id.widgetControlStatus, null)
            setOnClickPendingIntent(R.id.widgetControlFullStatus, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val buttonWidth = if (size == DjiWidgetSize.SMALL) 59f else 72f
                for (id in listOf(R.id.widgetControlKeepAwake, R.id.widgetControlSettings)) {
                    setViewLayoutWidth(id, buttonWidth, TypedValue.COMPLEX_UNIT_DIP)
                }
                if (size == DjiWidgetSize.FULL) {
                    val resources = context.resources
                    val sideWidth = fullControlUtilityWidth(config, resources.displayMetrics.density)
                    for (id in listOf(R.id.widgetControlFullKeepAwake, R.id.widgetControlMore)) {
                        setViewLayoutWidth(id, sideWidth, TypedValue.COMPLEX_UNIT_DIP)
                    }
                    // The status stack sets the toolbar minimum on short full fields.
                    val utilityHeight = if (nominalRowHeight >= toolbarMinimum) nominalRowHeight
                    else android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    setViewLayoutHeight(R.id.widgetControlFullUtility, utilityHeight.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                }
            }
            if (interactive) {
                setOnClickPendingIntent(R.id.widgetControlSettings, openAppIntent(context, 314))
                setOnClickPendingIntent(R.id.widgetControlMessageSettings, openAppIntent(context, 315))
            }

            when {
                size == DjiWidgetSize.SMALL_HALF && connected -> {
                    showCompactControls(state)
                    showControlStatus(context, config, size, viewState, palette, interactive)
                    setViewVisibility(R.id.widgetControlKeepAwake, View.GONE)
                    setViewVisibility(R.id.widgetControlSettings, View.GONE)
                }

                size == DjiWidgetSize.FULL && viewState.sleepConfirmation && connected -> {
                    showFullUtility(state)
                    showControlStatus(context, config, size, viewState, palette, interactive)
                    showFullControls(context, config, viewState, fullButtonHeight, interactive = false)
                    setViewVisibility(R.id.widgetControlFullSleep, View.GONE)
                    setViewVisibility(R.id.widgetControlTopConfirm, View.VISIBLE)
                    setInt(R.id.widgetControlTopConfirmYes, "setBackgroundResource", R.drawable.widget_surface_success)
                    setInt(R.id.widgetControlTopConfirmNo, "setBackgroundResource", R.drawable.widget_surface_danger)
                    if (interactive) {
                        setOnClickPendingIntent(
                            R.id.widgetControlTopConfirmYes,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_SLEEP_CONFIRM, 302),
                        )
                        setOnClickPendingIntent(
                            R.id.widgetControlTopConfirmNo,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_SLEEP_CANCEL, 303),
                        )
                    }
                }

                viewState.sleepConfirmation && connected -> {
                    setViewVisibility(R.id.widgetControlConfirm, View.VISIBLE)
                    setInt(R.id.widgetControlConfirmYes, "setBackgroundResource", R.drawable.widget_surface_success)
                    setInt(R.id.widgetControlConfirmNo, "setBackgroundResource", R.drawable.widget_surface_danger)
                    if (interactive) {
                        setOnClickPendingIntent(
                            R.id.widgetControlConfirmYes,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_SLEEP_CONFIRM, 302),
                        )
                        setOnClickPendingIntent(
                            R.id.widgetControlConfirmNo,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_SLEEP_CANCEL, 303),
                        )
                    }
                }

                size == DjiWidgetSize.FULL && viewState.controlFeedback != null -> {
                    showFullUtility(state)
                    showFullControls(context, config, viewState, fullButtonHeight, interactive)
                    setViewVisibility(R.id.widgetControlFullStatus, View.GONE)
                    setViewVisibility(R.id.widgetControlMore, View.GONE)
                    setViewVisibility(R.id.widgetControlFeedback, View.VISIBLE)
                    setTextViewText(R.id.widgetControlFeedback, viewState.controlFeedback)
                }

                viewState.controlFeedback != null -> {
                    showControlMessage(viewState.controlFeedback)
                    setOnClickPendingIntent(R.id.widgetControlMessage, null)
                    setViewVisibility(R.id.widgetControlMessageSettings, View.GONE)
                }

                size == DjiWidgetSize.FULL && !connected -> {
                    showFullTopMessage(
                        connectionLabel(context, viewState),
                    )
                    val connectBackground = if (state.configured) {
                        R.drawable.widget_surface_selector
                    } else {
                        R.drawable.widget_surface_dark
                    }
                    setInt(R.id.widgetControlTopMessage, "setBackgroundResource", connectBackground)
                    showFullControls(context, config, viewState, fullButtonHeight, interactive)
                    if (interactive) {
                        setOnClickPendingIntent(R.id.widgetControlTopMessage, connectionIntent(context, state.configured))
                        setOnClickPendingIntent(R.id.widgetControlOfflineMore, openAppIntent(context, 312))
                    }
                }

                !connected -> {
                    val connectBackground = if (state.configured) {
                        R.drawable.widget_surface_selector
                    } else {
                        R.drawable.widget_surface_dark
                    }
                    setInt(R.id.widgetControlMessage, "setBackgroundResource", connectBackground)
                    showControlMessage(
                        connectionLabel(context, viewState),
                    )
                    if (size == DjiWidgetSize.SMALL_HALF) {
                        setViewVisibility(R.id.widgetControlMessageSettings, View.GONE)
                    }
                    if (interactive) {
                        setOnClickPendingIntent(R.id.widgetControlMessage, connectionIntent(context, state.configured))
                    }
                }

                else -> {
                    if (size == DjiWidgetSize.FULL) {
                        showFullUtility(state)
                        showControlStatus(context, config, size, viewState, palette, interactive)
                        showFullControls(context, config, viewState, fullButtonHeight, interactive)
                    } else {
                        showCompactControls(state)
                        showControlStatus(context, config, size, viewState, palette, interactive)
                    }
                    if (interactive) {
                        val keepAwakeId = if (size == DjiWidgetSize.FULL) {
                            R.id.widgetControlFullKeepAwake
                        } else {
                            R.id.widgetControlKeepAwake
                        }
                        setOnClickPendingIntent(keepAwakeId, actionIntent(context, DjiWidgetActionReceiver.ACTION_KEEP_AWAKE_TOGGLE, 306))
                        if (size == DjiWidgetSize.FULL) {
                            setOnClickPendingIntent(R.id.widgetControlFullSleep, actionIntent(context, DjiWidgetActionReceiver.ACTION_SLEEP_REQUEST, 307))
                        }
                        if (size == DjiWidgetSize.FULL) {
                            setOnClickPendingIntent(R.id.widgetControlMore, openAppIntent(context, 313))
                        }

                    }
                }
            }

            setTextViewTextSize(
                R.id.widgetControlMessageText,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            if (viewState.controlFeedback != null) {
                setTextViewTextSize(R.id.widgetControlMessageText, TypedValue.COMPLEX_UNIT_SP, 20f)
                setInt(R.id.widgetControlMessage, "setBackgroundResource", R.drawable.widget_surface_dark)
            }
            setTextColor(R.id.widgetControlMessageText, palette.textPrimary)
            setTextViewTextSize(R.id.widgetControlTopMessageText, TypedValue.COMPLEX_UNIT_SP, statusTextSize(config, size))
            setTextColor(R.id.widgetControlTopMessageText, palette.textPrimary)
            setTextViewTextSize(
                R.id.widgetControlTopConfirmText,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            setTextColor(R.id.widgetControlTopConfirmText, palette.textPrimary)
            if (size == DjiWidgetSize.FULL) {
                val rowHeight = (config.viewSize.second - FIELD_VERTICAL_INSET -
                    context.resources.getDimensionPixelSize(R.dimen.widget_control_total_gaps)).coerceAtLeast(0) / 4
                setTextViewTextSize(R.id.widgetControlTopMessageText, TypedValue.COMPLEX_UNIT_PX,
                    minOf(statusTextSize(config, size) * context.resources.displayMetrics.scaledDensity, rowHeight * .4f))
                setTextViewTextSize(R.id.widgetControlFullOfflineHint, TypedValue.COMPLEX_UNIT_PX,
                    minOf(12f * context.resources.displayMetrics.scaledDensity, rowHeight * .3f))
            }
        }
    }

    private fun RemoteViews.showControlMessage(message: String) {
        setViewVisibility(R.id.widgetControlMessageContainer, View.VISIBLE)
        setTextViewText(R.id.widgetControlMessageText, message)
    }

    private fun RemoteViews.showFullTopMessage(message: String) {
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullOffline, View.VISIBLE)
        setTextViewText(R.id.widgetControlTopMessageText, message)
        setTextViewText(R.id.widgetControlFullOfflineHint, "")
    }

    private fun RemoteViews.showCompactControls(state: DjiCameraState) {
        setViewVisibility(R.id.widgetControlMain, View.VISIBLE)
        setImageViewResource(
            R.id.widgetControlKeepAwakeIcon,
            if (state.keepAwakeEnabled) R.drawable.ic_widget_keep_awake else R.drawable.ic_widget_keep_awake_disabled,
        )
    }

    private fun RemoteViews.showFullUtility(state: DjiCameraState) {
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullUtility, View.VISIBLE)
        setImageViewResource(
            R.id.widgetControlFullKeepAwakeIcon,
            if (state.keepAwakeEnabled) R.drawable.ic_widget_keep_awake else R.drawable.ic_widget_keep_awake_disabled,
        )
    }

    private fun RemoteViews.showControlStatus(
        context: Context,
        config: ViewConfig,
        size: DjiWidgetSize,
        viewState: DjiWidgetViewState,
        palette: DjiPalette,
        interactive: Boolean,
    ) {
        val full = size == DjiWidgetSize.FULL
        val state = viewState.camera
        val stacked = full || size == DjiWidgetSize.LARGE || size == DjiWidgetSize.MEDIUM
        val panel = if (full) R.id.widgetControlFullStatus else R.id.widgetControlStatus
        val battery = if (full) R.id.widgetControlFullBattery else R.id.widgetControlBattery
        val batteryIcon = if (full) R.id.widgetControlFullBatteryIcon else R.id.widgetControlBatteryIcon
        val storage = if (full) R.id.widgetControlFullStorage else R.id.widgetControlStorage
        val storageGroup = if (full) R.id.widgetControlFullStorageGroup else R.id.widgetControlStorageGroup
        val resolution = if (full) R.id.widgetControlFullResolution else R.id.widgetControlResolution
        val resolutionIcon = if (full) R.id.widgetControlFullResolutionIcon else R.id.widgetControlResolutionIcon
        val chevron = if (full) R.id.widgetControlFullRefreshChevron else R.id.widgetControlRefreshChevron
        val resources = context.resources
        val gap = resources.getDimensionPixelSize(R.dimen.widget_control_status_gap)
        val iconSize = resources.getDimensionPixelSize(R.dimen.widget_control_status_icon)
        val density = resources.displayMetrics.density
        val sideWidth = when (size) {
            DjiWidgetSize.FULL -> fullControlUtilityWidth(config, density) + 6f
            DjiWidgetSize.SMALL_HALF -> 0f
            DjiWidgetSize.SMALL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 65f else 78f
            else -> 78f
        }
        val available = config.viewSize.first - FIELD_HORIZONTAL_INSET - 2 * sideWidth * density
        val textSize = if (full) R.dimen.widget_control_full_status_text else R.dimen.widget_control_status_text
        val paint = Paint().apply { this.textSize = resources.getDimension(textSize) }
        val label = if (state.hasCamControlVideoProfile()) {
            val mapped = checkNotNull(videoResolutionLabel(state.videoResolution))
            val format = mapped.substringBefore(' ')
            val ratio = mapped.substringAfter(' ', "16:9")
            val fps = videoFpsLabel(state.videoFps)
            listOf(
                "$format · $fps fps · $ratio", "$format/$fps · $ratio", "$format · $fps fps", "$format/$fps", format,
            ).firstOrNull { paint.measureText(it) <= available - iconSize - 4 * density } ?: format
        } else null
        val refreshing = viewState.refreshingVideoSettings
        val canRefresh = label == null && !state.recording && !refreshing
        val text = when {
            label != null -> label
            refreshing && state.cameraMode != DjiCameraMode.VIDEO -> {
                val message = context.getString(R.string.widget_switching_to_video)
                if (paint.measureText(message) <= available - 4 * density) message
                else context.getString(R.string.widget_switching_to_video_short)
            }
            refreshing -> context.getString(R.string.widget_refreshing_video)
            canRefresh -> context.getString(R.string.widget_refresh_video)
            else -> "—"
        }

        setViewVisibility(panel, View.VISIBLE)
        if (!full) {
            val layout = if (stacked) R.layout.widget_control_values_vertical else R.layout.widget_control_values_horizontal
            removeAllViews(R.id.widgetControlStatusValues)
            addView(R.id.widgetControlStatusValues, RemoteViews(context.packageName, layout))
            centerWidgetText(battery, storage)
            setInt(panel, "setGravity", if (stacked) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER)
        }
        setViewPadding(storageGroup, if (stacked) 0 else 3 * gap, if (stacked) gap else 0, 0, 0)
        setTextViewText(battery, state.batteryPercent?.let { "$it%" } ?: "--%")
        val batteryTint = if ((state.batteryPercent ?: 100) < 20) palette.batteryLow else palette.textPrimary
        setImageViewBitmap(batteryIcon, camControlBatteryIcon(iconSize, state.batteryPercent, batteryTint))
        setTextViewText(storage, formatStorage(context, state.remainingCapacityMb))
        setTextViewText(resolution, text)
        setViewVisibility(resolutionIcon, if (refreshing) View.GONE else View.VISIBLE)
        for (id in listOf(battery, storage, resolution)) setTextColor(id, palette.textPrimary)
        setViewVisibility(chevron, if (canRefresh) View.VISIBLE else View.GONE)
        if (interactive && canRefresh) {
            setOnClickPendingIntent(panel, actionIntent(context, DjiWidgetActionReceiver.ACTION_REFRESH_VIDEO_SETTINGS, 316))
        }
    }

    private fun fullControlUtilityWidth(config: ViewConfig, density: Float): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            minOf(72f, (config.viewSize.first - FIELD_HORIZONTAL_INSET) / density * .30f)
        } else 72f

    private fun camControlBatteryIcon(size: Int, percent: Int?, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(size / 24f, size / 24f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 1.5f }
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(1.5f, 6.5f, 20f, 17.5f, 2f, 2f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(21f, 9.5f, 23f, 14.5f, 1f, 1f, paint)
        val fill = (percent ?: 0).coerceIn(0, 100) / 100f
        canvas.drawRect(4f, 9f, 4f + 13.5f * fill, 15f, paint)
        return bitmap
    }

    private fun RemoteViews.showFullControls(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        buttonHeight: Int,
        interactive: Boolean,
    ) {
        val connected = viewState.camera.connection == CameraConnectionState.CONNECTED
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFull, View.VISIBLE)
        setViewVisibility(R.id.widgetControlPhoto, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullSleep, View.VISIBLE)
        setBoolean(R.id.widgetControlFullSleep, "setEnabled", connected)
        setImageViewResource(R.id.widgetControlFullSleepIcon,
            if (connected) R.drawable.ic_widget_sleep else R.drawable.ic_widget_sleep_disabled)
        setInt(R.id.widgetControlFullSleep, "setBackgroundResource",
            if (connected) R.drawable.widget_surface_selector else R.drawable.widget_surface_dark)
        renderVideoButton(context, config, viewState, buttonHeight, interactive, hasConnectionButton = true)
        renderPhotoButton(context, config, viewState, interactive, hasConnectionButton = true)
    }

    private fun fullControlStatusHeight(context: Context): Int {
        val resources = context.resources
        val gap = resources.getDimensionPixelSize(R.dimen.widget_control_status_gap)
        val icon = resources.getDimensionPixelSize(R.dimen.widget_control_status_icon)
        val metrics = Paint().apply {
            textSize = resources.getDimension(R.dimen.widget_control_full_status_text)
        }.fontMetricsInt
        return 3 * maxOf(icon, metrics.descent - metrics.ascent) + 2 * gap
    }

    private fun RemoteViews.applyWidgetFrame(rootId: Int, showCaption: Boolean = false) {
        // Total inset stays 12px, so every shared button keeps its measured space.
        setViewPadding(rootId, 10, if (showCaption) 4 else 6, 10, if (showCaption) 8 else 6)
    }

    private fun RemoteViews.applyWidgetFrameForCaption(showCaption: Boolean) {
        val rootId = when (layoutId) {
            R.layout.widget_dji_video -> R.id.widgetVideoRoot
            R.layout.widget_dji_photo -> R.id.widgetPhotoRoot
            R.layout.widget_dji_video_photo -> R.id.widgetVideoPhotoRoot
            R.layout.widget_dji_control -> R.id.widgetControlRoot
            else -> return
        }
        applyWidgetFrame(rootId, showCaption)
    }

    private fun RemoteViews.applyIconPadding(
        context: Context,
        paddingRes: Int,
        vararg iconIds: Int,
    ) {
        val appliedPadding = context.resources.getDimensionPixelSize(paddingRes)
        iconIds.forEach { setViewPadding(it, appliedPadding, appliedPadding, appliedPadding, appliedPadding) }
    }

}

private fun compactFeedback(context: Context, feedback: String?): String {
    return when {
        feedback.isNullOrBlank() -> context.getString(R.string.widget_highlight_feedback)
        feedback.contains("SPEED", ignoreCase = true) -> context.getString(R.string.widget_speed_feedback)
        feedback.contains("HIGHLIGHT", ignoreCase = true) -> context.getString(R.string.widget_highlight_feedback)
        else -> feedback
    }
}

fun videoResolutionLabel(value: Int?): String? = when (value) {
    10 -> "1080P"
    16 -> "4K"
    45 -> "2.7K"
    66 -> "1080P 9:16"
    67 -> "2.7K 9:16"
    95 -> "2.7K 4:3"
    103 -> "4K 4:3"
    109 -> "4K 9:16"
    else -> null
}

fun videoFpsLabel(value: Int?): String? = when (value) {
    1 -> "24"
    2 -> "25"
    3 -> "30"
    4 -> "48"
    5 -> "50"
    6 -> "60"
    7 -> "120"
    8 -> "240"
    10 -> "100"
    19 -> "200"
    else -> null
}

internal fun DjiCameraState.hasCamControlVideoProfile(): Boolean =
    connection == CameraConnectionState.CONNECTED &&
        videoResolutionLabel(videoResolution) != null && videoFpsLabel(videoFps) != null

private fun statusTextSize(config: ViewConfig, size: DjiWidgetSize): Float {
    val maximum = when (size) {
        DjiWidgetSize.SMALL_HALF, DjiWidgetSize.SMALL -> 15
        DjiWidgetSize.MEDIUM -> 18
        DjiWidgetSize.LARGE, DjiWidgetSize.FULL -> 21
    }
    return (config.textSize / 2).coerceIn(12, maximum).toFloat()
}

private fun formatStorage(context: Context, megabytes: Long?): String {
    return when {
        megabytes == null -> context.getString(R.string.storage_unknown)
        megabytes >= 1_024 -> context.getString(R.string.storage_gb_format, megabytes / 1_024.0)
        else -> context.getString(R.string.storage_mb_format, megabytes)
    }
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private const val FIELD_HORIZONTAL_INSET = 20
private const val FIELD_VERTICAL_INSET = 12

private fun connectionLabel(context: Context, state: DjiWidgetViewState): String =
    context.getString(if (state.connectPrompts.isNotEmpty()) R.string.widget_searching else R.string.widget_connect)

private fun connectionIntent(context: Context, configured: Boolean): PendingIntent =
    if (configured) actionIntent(context, DjiWidgetActionReceiver.ACTION_CONNECT, 304) else openAppIntent(context, 305)

private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DjiWidgetActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
    return PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun RemoteViews.centerWidgetText(vararg ids: Int) {
    for (id in ids) {
        // The layouts use textAlignment=gravity; RemoteViews cannot change textAlignment itself.
        setInt(id, "setGravity", Gravity.CENTER)
    }
}
