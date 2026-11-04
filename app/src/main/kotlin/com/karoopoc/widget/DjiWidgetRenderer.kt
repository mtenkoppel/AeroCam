package com.karoopoc.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.karoopoc.DjiPalette
import com.karoopoc.DjiTheme
import com.karoopoc.MainActivity
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
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
    /** Compose the real controls; recording uses the standalone video view unchanged. */
    fun videoPhoto(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews {
        if (viewState.camera.recording) return video(context, config, viewState, interactive)

        val gap = context.resources.getDimensionPixelSize(R.dimen.widget_combined_gap)
        val slotConfig = config.copy(
            gridSize = Pair((config.gridSize.first / 2).coerceAtLeast(1), config.gridSize.second),
            viewSize = Pair(
                ((config.viewSize.first - 20 - gap) / 2).coerceAtLeast(1),
                (config.viewSize.second - 20).coerceAtLeast(1),
            ),
        )
        fun RemoteViews.withoutOuterFrame(rootId: Int) = apply {
            // The combined container owns the one outer frame. Keep child surfaces,
            // selectors, PendingIntents and all state-dependent rendering intact.
            setViewPadding(rootId, 0, 0, 0, 0)
            setInt(rootId, "setBackgroundColor", Color.TRANSPARENT)
        }
        return RemoteViews(context.packageName, R.layout.widget_dji_video_photo).apply {
            setInt(R.id.widgetVideoPhotoRoot, "setBackgroundResource", DjiTheme.widgetBackgroundResource(context))
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
    ): RemoteViews {
        val state = viewState.camera
        val palette = DjiTheme.palette(context)
        val size = DjiWidgetLayout.sizeFor(config)
        val connected = state.connection == CameraConnectionState.CONNECTED
        val recording = state.recording
        val feedback = state.highlightFeedback?.uppercase()
        val recordingLayout = recordingLayoutFor(config, size)
        val connectPrompt = ConnectPrompt.VIDEO in viewState.connectPrompts
        val awaitingResponse = viewState.videoAwaitingResponse

        return RemoteViews(context.packageName, R.layout.widget_dji_video).apply {
            setInt(R.id.widgetVideoRoot, "setBackgroundResource", DjiTheme.widgetBackgroundResource(context))
            applyWidgetFrame(R.id.widgetVideoRoot)
            applyIconPadding(context, R.dimen.widget_icon_primary_padding, R.id.widgetVideoIcon)
            applyIconPadding(context, R.dimen.widget_icon_utility_padding, R.id.widgetVideoHighlightIcon)
            setTextViewTextSize(
                R.id.widgetVideoTimer,
                TypedValue.COMPLEX_UNIT_SP,
                recordingPrimaryTextSize(config, recordingLayout),
            )
            setTextViewTextSize(
                R.id.widgetVideoStatus,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            setTextViewTextSize(
                R.id.widgetVideoMeta,
                TypedValue.COMPLEX_UNIT_SP,
                recordingMetaTextSize(recordingLayout),
            )
            setTextColor(R.id.widgetVideoStatus, palette.textPrimary)
            setTextColor(R.id.widgetVideoTimer, palette.textOnAccent)
            setTextColor(R.id.widgetVideoMeta, palette.textOnAccent)

            setViewVisibility(R.id.widgetVideoIdle, View.GONE)
            setViewVisibility(R.id.widgetVideoStatus, View.GONE)
            setViewVisibility(R.id.widgetVideoRecording, View.GONE)
            setViewVisibility(R.id.widgetVideoHighlight, View.GONE)
            setViewVisibility(R.id.widgetVideoSpinner, View.GONE)

            when {
                awaitingResponse != null -> {
                    setInt(
                        R.id.widgetVideoPrimary,
                        "setBackgroundResource",
                        if (recording) R.drawable.widget_surface_recording else R.drawable.widget_surface_selector,
                    )
                    setViewVisibility(R.id.widgetVideoSpinner, View.VISIBLE)
                    if (recording && size != DjiWidgetSize.SMALL_HALF) {
                        setViewVisibility(R.id.widgetVideoHighlight, View.VISIBLE)
                        setInt(
                            R.id.widgetVideoHighlight,
                            "setBackgroundResource",
                            R.drawable.widget_surface_selector,
                        )
                    }
                }

                recording -> {
                    setViewVisibility(R.id.widgetVideoRecording, View.VISIBLE)
                    setInt(R.id.widgetVideoPrimary, "setBackgroundResource", R.drawable.widget_surface_recording)
                    val effectiveLayout = when {
                        recordingLayout == VideoRecordingLayout.ICON_ONLY && feedback != null -> VideoRecordingLayout.ICON_ONLY
                        recordingLayout == VideoRecordingLayout.ICON_ONLY -> VideoRecordingLayout.COMPACT
                        recordingLayout == VideoRecordingLayout.COMPACT && feedback != null -> VideoRecordingLayout.FEEDBACK_COMPACT
                        else -> recordingLayout
                    }
                    setTextViewTextSize(
                        R.id.widgetVideoTimer,
                        TypedValue.COMPLEX_UNIT_SP,
                        recordingPrimaryTextSize(config, effectiveLayout),
                    )
                    setTextViewTextSize(
                        R.id.widgetVideoMeta,
                        TypedValue.COMPLEX_UNIT_SP,
                        recordingMetaTextSize(effectiveLayout),
                    )
                    setRecordingPadding(effectiveLayout)
                    when (effectiveLayout) {
                        VideoRecordingLayout.DETAILED -> {
                            setTextViewText(R.id.widgetVideoTimer, formatDuration(state.recordingDurationSeconds))
                            setViewVisibility(R.id.widgetVideoMeta, View.VISIBLE)
                            setTextViewText(
                                R.id.widgetVideoMeta,
                                feedback?.let { "★ ${compactFeedback(context, it)}" } ?: recordingMeta(context, state, size),
                            )
                        }

                        VideoRecordingLayout.COMPACT -> {
                            setTextViewText(R.id.widgetVideoTimer, formatDuration(state.recordingDurationSeconds))
                            setViewVisibility(R.id.widgetVideoMeta, View.GONE)
                        }

                        VideoRecordingLayout.FEEDBACK_COMPACT -> {
                            setTextViewText(R.id.widgetVideoTimer, "★ ${compactFeedback(context, feedback)}")
                            setViewVisibility(R.id.widgetVideoMeta, View.GONE)
                        }

                        VideoRecordingLayout.ICON_ONLY -> {
                            setTextViewText(R.id.widgetVideoTimer, "★")
                            setViewVisibility(R.id.widgetVideoMeta, View.GONE)
                        }
                    }
                    if (size != DjiWidgetSize.SMALL_HALF) {
                        setViewVisibility(R.id.widgetVideoHighlight, View.VISIBLE)
                        setInt(
                            R.id.widgetVideoHighlight,
                            "setBackgroundResource",
                            R.drawable.widget_surface_selector,
                        )
                    }
                }

                connectPrompt -> {
                    setInt(R.id.widgetVideoPrimary, "setBackgroundResource", R.drawable.widget_surface_dark)
                    setViewVisibility(R.id.widgetVideoStatus, View.VISIBLE)
                    setTextViewText(R.id.widgetVideoStatus, context.getString(R.string.widget_connecting))
                }

                else -> {
                    setViewVisibility(R.id.widgetVideoIdle, View.VISIBLE)
                    setImageViewResource(
                        R.id.widgetVideoIcon,
                        if (connected) R.drawable.ic_widget_video else R.drawable.ic_widget_video_disabled,
                    )
                    setInt(
                        R.id.widgetVideoPrimary,
                        "setBackgroundResource",
                        if (connected) R.drawable.widget_surface_selector else R.drawable.widget_surface_dark,
                    )
                }
            }

            if (interactive) {
                setOnClickPendingIntent(
                    R.id.widgetVideoPrimary,
                    if (state.configured) {
                        actionIntent(context, DjiWidgetActionReceiver.ACTION_RECORD_TOGGLE, 101)
                    } else {
                        openAppIntent(context, 101)
                    },
                )
                setOnClickPendingIntent(
                    R.id.widgetVideoHighlight,
                    actionIntent(context, DjiWidgetActionReceiver.ACTION_HIGHLIGHT, 102),
                )
            }
        }
    }

    fun photo(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews {
        val state = viewState.camera
        val palette = DjiTheme.palette(context)
        val size = DjiWidgetLayout.sizeFor(config)
        val connected = state.connection == CameraConnectionState.CONNECTED
        val connectPrompt = ConnectPrompt.PHOTO in viewState.connectPrompts
        val awaitingResponse = viewState.photoAwaitingResponse
        val photoBlocked = state.recording

        return RemoteViews(context.packageName, R.layout.widget_dji_photo).apply {
            setInt(R.id.widgetPhotoRoot, "setBackgroundResource", DjiTheme.widgetBackgroundResource(context))
            applyWidgetFrame(R.id.widgetPhotoRoot)
            applyIconPadding(context, R.dimen.widget_icon_primary_padding, R.id.widgetPhotoIcon)
            setTextViewTextSize(
                R.id.widgetPhotoStatus,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            setTextColor(R.id.widgetPhotoStatus, palette.textPrimary)
            setViewVisibility(R.id.widgetPhotoIcon, View.GONE)
            setViewVisibility(R.id.widgetPhotoStatus, View.GONE)
            setViewVisibility(R.id.widgetPhotoSpinner, View.GONE)

            when {
                awaitingResponse != null -> {
                    setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", R.drawable.widget_surface_selector)
                    setViewVisibility(R.id.widgetPhotoSpinner, View.VISIBLE)
                }

                photoBlocked -> {
                    setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", R.drawable.widget_surface_dark)
                    setViewVisibility(R.id.widgetPhotoIcon, View.VISIBLE)
                    setImageViewResource(R.id.widgetPhotoIcon, R.drawable.ic_widget_photo_blocked)
                }

                state.photoState == PhotoCaptureState.COMPLETE -> {
                    setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", R.drawable.widget_surface_success)
                    setViewVisibility(R.id.widgetPhotoIcon, View.VISIBLE)
                    setImageViewResource(R.id.widgetPhotoIcon, R.drawable.ic_widget_photo_success)
                }

                state.photoState == PhotoCaptureState.REQUESTED ||
                    state.photoState == PhotoCaptureState.CAPTURING -> {
                    setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", R.drawable.widget_surface_selector)
                    setViewVisibility(R.id.widgetPhotoSpinner, View.VISIBLE)
                }

                connectPrompt -> {
                    setInt(R.id.widgetPhotoPrimary, "setBackgroundResource", R.drawable.widget_surface_dark)
                    setViewVisibility(R.id.widgetPhotoStatus, View.VISIBLE)
                    setTextViewText(R.id.widgetPhotoStatus, context.getString(R.string.widget_connecting))
                }

                else -> {
                    setInt(
                        R.id.widgetPhotoPrimary,
                        "setBackgroundResource",
                        if (connected) R.drawable.widget_surface_selector else R.drawable.widget_surface_dark,
                    )
                    setViewVisibility(R.id.widgetPhotoIcon, View.VISIBLE)
                    setImageViewResource(
                        R.id.widgetPhotoIcon,
                        if (connected) R.drawable.ic_widget_photo else R.drawable.ic_widget_photo_disabled,
                    )
                }
            }

            if (interactive && !photoBlocked) {
                setOnClickPendingIntent(
                    R.id.widgetPhotoPrimary,
                    if (state.configured) {
                        actionIntent(context, DjiWidgetActionReceiver.ACTION_PHOTO, 201)
                    } else {
                        openAppIntent(context, 201)
                    },
                )
            }
        }
    }

    fun cameraControl(
        context: Context,
        config: ViewConfig,
        viewState: DjiWidgetViewState,
        interactive: Boolean = true,
    ): RemoteViews {
        val state = viewState.camera
        val palette = DjiTheme.palette(context)
        val size = DjiWidgetLayout.sizeFor(config)
        val connected = state.connection == CameraConnectionState.CONNECTED
        val connectPrompt = ConnectPrompt.CONTROL in viewState.connectPrompts

        return RemoteViews(context.packageName, R.layout.widget_dji_control).apply {
            setInt(R.id.widgetControlRoot, "setBackgroundResource", DjiTheme.widgetBackgroundResource(context))
            applyWidgetFrame(R.id.widgetControlRoot)
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
                R.id.widgetControlVideoHighlightIcon,
            )
            applyIconPadding(
                context,
                R.dimen.widget_icon_primary_padding,
                R.id.widgetControlVideoIdleIcon,
                R.id.widgetControlVideoRecordingIcon,
                R.id.widgetControlPhotoIcon,
            )
            applyIconPadding(
                context,
                R.dimen.widget_icon_status_padding,
                R.id.widgetControlFullBatteryIcon,
                R.id.widgetControlFullStorageIcon,
            )
            setViewVisibility(R.id.widgetControlMain, View.GONE)
            setViewVisibility(R.id.widgetControlMessageContainer, View.GONE)
            setViewVisibility(R.id.widgetControlConfirm, View.GONE)
            setViewVisibility(R.id.widgetControlFullShell, View.GONE)
            setViewVisibility(R.id.widgetControlFullUtility, View.GONE)
            setViewVisibility(R.id.widgetControlFullOffline, View.GONE)
            setViewVisibility(R.id.widgetControlTopConfirm, View.GONE)
            setViewVisibility(R.id.widgetControlTopConfirmSpacer, View.GONE)
            setViewVisibility(R.id.widgetControlFullStatus, View.GONE)
            setViewVisibility(R.id.widgetControlFull, View.GONE)
            setViewVisibility(R.id.widgetControlPhoto, View.GONE)
            setViewVisibility(R.id.widgetControlFullSleep, View.GONE)
            if (interactive) {
                setOnClickPendingIntent(R.id.widgetControlSettings, openAppIntent(context, 314))
                setOnClickPendingIntent(R.id.widgetControlMessageSettings, openAppIntent(context, 315))
            }

            when {
                size == DjiWidgetSize.SMALL_HALF -> {
                    showControlMessage(context.getString(R.string.widget_full_width_only))
                    if (interactive) {
                        setOnClickPendingIntent(R.id.widgetControlMessage, openAppIntent(context, 301))
                    }
                }

                size == DjiWidgetSize.FULL && viewState.sleepConfirmation && connected -> {
                    setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
                    setViewVisibility(R.id.widgetControlTopConfirm, View.VISIBLE)
                    setViewVisibility(R.id.widgetControlTopConfirmSpacer, View.VISIBLE)
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
                    showFullTopMessage(viewState.controlFeedback)
                    showFullStatus(
                        context,
                        state,
                        connected = true,
                        palette = palette,
                        showReconnectHint = false,
                    )
                    showFullControls(context, state, viewState, connected = true, palette = palette)
                    if (interactive) {
                        setOnClickPendingIntent(R.id.widgetControlOfflineMore, openAppIntent(context, 311))
                    }
                }

                viewState.controlFeedback != null -> showControlMessage(viewState.controlFeedback)

                size == DjiWidgetSize.FULL && !connected -> {
                    showFullTopMessage(
                        when {
                            connectPrompt -> context.getString(R.string.widget_connecting)
                            !state.configured -> context.getString(R.string.widget_not_configured)
                            else -> context.getString(R.string.widget_connect)
                        },
                    )
                    val connectBackground = if (state.configured) {
                        R.drawable.widget_surface_selector
                    } else {
                        R.drawable.widget_surface_dark
                    }
                    setInt(R.id.widgetControlTopMessage, "setBackgroundResource", connectBackground)
                    showFullStatus(
                        context,
                        state,
                        connected = false,
                        palette = palette,
                        showReconnectHint = state.configured,
                    )
                    showFullControls(context, state, viewState, connected = false, palette = palette)
                    if (interactive) {
                        val action = if (state.configured) {
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_CONNECT, 304)
                        } else {
                            openAppIntent(context, 305)
                        }
                        setOnClickPendingIntent(R.id.widgetControlTopMessage, action)
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
                        when {
                            connectPrompt -> context.getString(R.string.widget_connecting)
                            !state.configured -> context.getString(R.string.widget_not_configured)
                            else -> context.getString(R.string.widget_connect)
                        },
                    )
                    if (interactive) {
                        val action = if (state.configured) {
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_CONNECT, 304)
                        } else {
                            openAppIntent(context, 305)
                        }
                        setOnClickPendingIntent(R.id.widgetControlMessage, action)
                    }
                }

                else -> {
                    if (size == DjiWidgetSize.FULL) {
                        showFullUtility(state)
                        showFullStatus(
                            context,
                            state,
                            connected = true,
                            palette = palette,
                            showReconnectHint = false,
                        )
                        showFullControls(context, state, viewState, connected = true, palette = palette)
                    } else {
                        showCompactControls(context, state, palette)
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
                        setOnClickPendingIntent(
                            R.id.widgetControlVideo,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_RECORD_TOGGLE, 308),
                        )
                        if (!state.recording) {
                            setOnClickPendingIntent(
                                R.id.widgetControlPhoto,
                                actionIntent(context, DjiWidgetActionReceiver.ACTION_PHOTO, 309),
                            )
                        }
                        setOnClickPendingIntent(
                            R.id.widgetControlVideoHighlight,
                            actionIntent(context, DjiWidgetActionReceiver.ACTION_HIGHLIGHT, 310),
                        )
                    }
                }
            }

            if (size == DjiWidgetSize.FULL) {
                // Equal weights reserve the fixed gaps before sizing the four rows.
                // Cap header text to half a row so shorter full-size fields still fit.
                val rowHeight = (config.viewSize.second - 20 -
                    context.resources.getDimensionPixelSize(R.dimen.widget_control_total_gaps)).coerceAtLeast(0) / 4
                val headerText = minOf(18f * context.resources.displayMetrics.scaledDensity, rowHeight * .4f).coerceAtLeast(1f)
                setTextViewTextSize(R.id.widgetControlFullBattery, TypedValue.COMPLEX_UNIT_PX, headerText)
                setTextViewTextSize(R.id.widgetControlFullStorage, TypedValue.COMPLEX_UNIT_PX, headerText)
            }
            setTextViewTextSize(
                R.id.widgetControlMessageText,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            setTextColor(R.id.widgetControlMessageText, palette.textPrimary)
            setTextViewTextSize(R.id.widgetControlTopMessageText, TypedValue.COMPLEX_UNIT_SP, statusTextSize(config, size))
            setTextColor(R.id.widgetControlTopMessageText, palette.textPrimary)
            setTextViewTextSize(
                R.id.widgetControlTopConfirmText,
                TypedValue.COMPLEX_UNIT_SP,
                statusTextSize(config, size),
            )
            setTextColor(R.id.widgetControlTopConfirmText, palette.textPrimary)
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
    }

    private fun RemoteViews.showCompactControls(context: Context, state: DjiCameraState, palette: DjiPalette) {
        setViewVisibility(R.id.widgetControlMain, View.VISIBLE)
        setTextViewText(R.id.widgetControlBattery, state.batteryPercent?.let { "$it%" } ?: "--%")
        setTextViewText(R.id.widgetControlStorage, formatStorage(context, state.remainingCapacityMb))
        setTextColor(R.id.widgetControlBattery, batteryColor(state.batteryPercent, palette))
        setTextColor(R.id.widgetControlStorage, palette.textPrimary)
        setImageViewResource(
            R.id.widgetControlKeepAwakeIcon,
            if (state.keepAwakeEnabled) R.drawable.ic_widget_keep_awake else R.drawable.ic_widget_keep_awake_disabled,
        )
        setInt(
            R.id.widgetControlKeepAwake,
            "setBackgroundResource",
            if (state.keepAwakeEnabled) R.drawable.widget_surface_active else R.drawable.widget_surface_selector,
        )
    }

    private fun RemoteViews.showFullUtility(state: DjiCameraState) {
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullUtility, View.VISIBLE)
        setImageViewResource(
            R.id.widgetControlFullKeepAwakeIcon,
            if (state.keepAwakeEnabled) R.drawable.ic_widget_keep_awake else R.drawable.ic_widget_keep_awake_disabled,
        )
        setInt(
            R.id.widgetControlFullKeepAwake,
            "setBackgroundResource",
            if (state.keepAwakeEnabled) R.drawable.widget_surface_active else R.drawable.widget_surface_selector,
        )
    }

    private fun RemoteViews.showFullStatus(
        context: Context,
        state: DjiCameraState,
        connected: Boolean,
        palette: DjiPalette,
        showReconnectHint: Boolean,
    ) {
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullStatus, View.VISIBLE)
        setViewVisibility(
            R.id.widgetControlFullOfflineHint,
            if (!connected && showReconnectHint) View.VISIBLE else View.GONE,
        )
        setViewVisibility(
            R.id.widgetControlFullBatteryGroup,
            if (connected) View.VISIBLE else View.GONE,
        )
        setViewVisibility(
            R.id.widgetControlFullStorageGroup,
            if (connected) View.VISIBLE else View.GONE,
        )
        setTextViewText(R.id.widgetControlFullOfflineHint, context.getString(R.string.widget_auto_reconnect_enabled))
        setTextColor(R.id.widgetControlFullOfflineHint, palette.textSecondary)
        setTextViewText(R.id.widgetControlFullBattery, state.batteryPercent?.let { "$it%" } ?: "--%")
        setTextViewText(R.id.widgetControlFullStorage, formatStorage(context, state.remainingCapacityMb))
        setTextColor(R.id.widgetControlFullBattery, batteryColor(state.batteryPercent, palette))
        setTextColor(R.id.widgetControlFullStorage, palette.textPrimary)
    }

    private fun RemoteViews.showFullControls(
        context: Context,
        state: DjiCameraState,
        viewState: DjiWidgetViewState,
        connected: Boolean,
        palette: DjiPalette,
    ) {
        setViewVisibility(R.id.widgetControlFullShell, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFull, View.VISIBLE)
        setViewVisibility(R.id.widgetControlPhoto, View.VISIBLE)
        setViewVisibility(R.id.widgetControlFullSleep, View.VISIBLE)
        setInt(R.id.widgetControlFullSleep, "setBackgroundResource",
            if (connected) R.drawable.widget_surface_selector else R.drawable.widget_surface_dark)
        setInt(
            R.id.widgetControlVideo,
            "setBackgroundResource",
            when {
                !connected -> R.drawable.widget_surface_dark
                state.recording -> R.drawable.widget_surface_recording
                else -> R.drawable.widget_surface_selector
            },
        )
        setViewVisibility(R.id.widgetControlVideoSpinner, View.GONE)
        setViewVisibility(R.id.widgetControlVideoIdle, View.VISIBLE)
        setViewVisibility(R.id.widgetControlVideoRecording, View.GONE)
        setViewVisibility(R.id.widgetControlVideoFeedback, View.GONE)
        setTextColor(R.id.widgetControlVideoTimer, palette.textOnAccent)
        setTextColor(R.id.widgetControlVideoFeedback, palette.textOnAccent)
        setImageViewResource(
            R.id.widgetControlVideoIdleIcon,
            if (connected) R.drawable.ic_widget_video else R.drawable.ic_widget_video_disabled,
        )
        setImageViewResource(R.id.widgetControlVideoRecordingIcon, R.drawable.ic_widget_video)
        if (connected && viewState.videoAwaitingResponse != null) {
            setViewVisibility(R.id.widgetControlVideoIdle, View.GONE)
            setViewVisibility(R.id.widgetControlVideoRecording, View.GONE)
            setViewVisibility(R.id.widgetControlVideoFeedback, View.GONE)
            setViewVisibility(R.id.widgetControlVideoSpinner, View.VISIBLE)
        } else if (connected && state.recording) {
            setViewVisibility(R.id.widgetControlVideoIdle, View.GONE)
            setViewVisibility(R.id.widgetControlVideoRecording, View.VISIBLE)
            setTextViewText(R.id.widgetControlVideoTimer, formatDuration(state.recordingDurationSeconds))
            state.highlightFeedback?.let {
                setViewVisibility(R.id.widgetControlVideoFeedback, View.VISIBLE)
                setTextViewText(R.id.widgetControlVideoFeedback, "★ ${compactFeedback(context, it.uppercase())}")
            }
        }
        setViewVisibility(
            R.id.widgetControlVideoHighlight,
            if (connected && state.recording) View.VISIBLE else View.GONE,
        )
        setInt(
            R.id.widgetControlPhoto,
            "setBackgroundResource",
            when {
                !connected || state.recording -> R.drawable.widget_surface_dark
                viewState.photoAwaitingResponse != null -> R.drawable.widget_surface_selector
                state.photoState == PhotoCaptureState.COMPLETE -> R.drawable.widget_surface_success
                else -> R.drawable.widget_surface_selector
            },
        )
        setViewVisibility(R.id.widgetControlPhotoSpinner, View.GONE)
        setViewVisibility(
            R.id.widgetControlPhotoIcon,
            when {
                connected && viewState.photoAwaitingResponse != null -> View.GONE
                else -> View.VISIBLE
            },
        )
        setImageViewResource(
            R.id.widgetControlPhotoIcon,
            if (!connected) {
                R.drawable.ic_widget_photo_disabled
            } else if (state.recording) {
                R.drawable.ic_widget_photo_blocked
            } else if (state.photoState == PhotoCaptureState.COMPLETE) {
                R.drawable.ic_widget_photo_success
            } else {
                R.drawable.ic_widget_photo
            },
        )
        setViewVisibility(
            R.id.widgetControlPhotoStatus,
            View.GONE,
        )
        if (connected && !state.recording && viewState.photoAwaitingResponse != null) {
            setViewVisibility(R.id.widgetControlPhotoSpinner, View.VISIBLE)
        }
    }

    private fun RemoteViews.applyWidgetFrame(rootId: Int) {
        setViewPadding(rootId, 10, 10, 10, 10)
    }

    private fun RemoteViews.applyIconPadding(
        context: Context,
        paddingRes: Int,
        vararg iconIds: Int,
    ) {
        val appliedPadding = context.resources.getDimensionPixelSize(paddingRes)
        iconIds.forEach { setViewPadding(it, appliedPadding, appliedPadding, appliedPadding, appliedPadding) }
    }

    private fun RemoteViews.setRecordingPadding(layout: VideoRecordingLayout) {
        when (layout) {
            VideoRecordingLayout.DETAILED -> setViewPadding(R.id.widgetVideoRecording, 6, 1, 6, 1)
            VideoRecordingLayout.COMPACT -> setViewPadding(R.id.widgetVideoRecording, 6, 2, 6, 2)
            VideoRecordingLayout.FEEDBACK_COMPACT -> setViewPadding(R.id.widgetVideoRecording, 6, 2, 6, 2)
            VideoRecordingLayout.ICON_ONLY -> setViewPadding(R.id.widgetVideoRecording, 4, 0, 4, 0)
        }
    }
}

private enum class VideoRecordingLayout {
    DETAILED,
    COMPACT,
    FEEDBACK_COMPACT,
    ICON_ONLY,
}

private fun recordingLayoutFor(config: ViewConfig, size: DjiWidgetSize): VideoRecordingLayout {
    return when {
        size == DjiWidgetSize.SMALL_HALF -> VideoRecordingLayout.ICON_ONLY
        config.viewSize.second < 128 -> VideoRecordingLayout.COMPACT
        config.viewSize.second < 165 -> VideoRecordingLayout.COMPACT
        else -> VideoRecordingLayout.DETAILED
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

private fun primaryTextSize(config: ViewConfig, size: DjiWidgetSize): Float {
    val maximum = when (size) {
        DjiWidgetSize.SMALL_HALF, DjiWidgetSize.SMALL -> 27
        DjiWidgetSize.MEDIUM -> 34
        DjiWidgetSize.LARGE, DjiWidgetSize.FULL -> 42
    }
    return config.textSize.coerceIn(22, maximum).toFloat()
}

private fun recordingPrimaryTextSize(config: ViewConfig, layout: VideoRecordingLayout): Float {
    val maximum = when (layout) {
        VideoRecordingLayout.DETAILED -> 38
        VideoRecordingLayout.COMPACT -> 30
        VideoRecordingLayout.FEEDBACK_COMPACT -> 24
        VideoRecordingLayout.ICON_ONLY -> 28
    }
    val minimum = when (layout) {
        VideoRecordingLayout.DETAILED -> 20
        VideoRecordingLayout.COMPACT -> 18
        VideoRecordingLayout.FEEDBACK_COMPACT -> 18
        VideoRecordingLayout.ICON_ONLY -> 20
    }
    return config.textSize.coerceIn(minimum, maximum).toFloat()
}

private fun recordingMetaTextSize(layout: VideoRecordingLayout): Float = when (layout) {
    VideoRecordingLayout.DETAILED -> 11f
    VideoRecordingLayout.COMPACT -> 0f
    VideoRecordingLayout.FEEDBACK_COMPACT -> 0f
    VideoRecordingLayout.ICON_ONLY -> 0f
}

private fun statusTextSize(config: ViewConfig, size: DjiWidgetSize): Float {
    val maximum = when (size) {
        DjiWidgetSize.SMALL_HALF, DjiWidgetSize.SMALL -> 15
        DjiWidgetSize.MEDIUM -> 18
        DjiWidgetSize.LARGE, DjiWidgetSize.FULL -> 21
    }
    return (config.textSize / 2).coerceIn(12, maximum).toFloat()
}

private fun recordingMeta(context: Context, state: DjiCameraState, size: DjiWidgetSize): String {
    return formatStorage(context, state.remainingCapacityMb)
}

private fun formatStorage(context: Context, megabytes: Long?): String {
    return when {
        megabytes == null -> context.getString(R.string.storage_unknown)
        megabytes >= 1_024 -> context.getString(R.string.storage_gb_format, megabytes / 1_024.0)
        else -> context.getString(R.string.storage_mb_format, megabytes)
    }
}

private fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun batteryColor(percent: Int?, palette: DjiPalette): Int = when {
    percent == null -> palette.textPrimary
    percent <= 20 -> palette.batteryLow
    else -> palette.textPrimary
}

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
