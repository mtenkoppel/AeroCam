package com.karoopoc.widget

import android.content.Context
import android.util.Log
import com.karoopoc.dji.DjiSettings
import com.karoopoc.R
import android.view.View
import android.widget.RemoteViews
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

abstract class DjiWidgetDataType(extension: String, typeId: String) : DataTypeImpl(extension, typeId) {
    override fun startStream(emitter: Emitter<StreamState>) {
        var tick = 0.0
        val timer: Timer = fixedRateTimer(
            name = "dji-widget-$typeId",
            daemon = true,
            initialDelay = 0L,
            period = 1_000L,
        ) {
            emitter.onNext(
                StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to tick++)),
                ),
            )
        }
        emitter.setCancellable { timer.cancel() }
    }

    protected fun observe(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
        caption: Int,
        render: (ViewConfig, DjiWidgetViewState) -> RemoteViews,
    ) {
        Log.i(TAG, "DJI field start type=$typeId size=${config.viewSize} grid=${config.gridSize}")
        val showCaption = !DjiSettings.hideFieldCaptions(context)
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val captionHeight = if (showCaption) (24 * context.resources.displayMetrics.density + .5f).toInt() else 0
        val bodyConfig = config.copy(viewSize = config.viewSize.first to (config.viewSize.second - captionHeight).coerceAtLeast(1))
        val cancel = DjiWidgetController.observe(context) {
            val body = render(bodyConfig, it)
            emitter.updateView(DjiWidgetRenderer.captionedField(context, caption, showCaption, body))
        }
        emitter.setCancellable(cancel)
    }
}

/** Video recording, timer and highlight control. The ID remains stable for existing profiles. */
class DjiCameraStatusDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter, R.string.dji_status) { fieldConfig, state ->
            DjiWidgetRenderer.video(context, fieldConfig, state)
        }
    }

    companion object {
        const val TYPE_ID = "dji-status"
    }
}

/** Camera connection, Keep Awake and deep-sleep control. */
class DjiPowerDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter, R.string.dji_power) { fieldConfig, state ->
            DjiWidgetRenderer.cameraControl(context, fieldConfig, state)
        }
    }

    companion object {
        const val TYPE_ID = "dji-power"
    }
}

/** Single-purpose photo shutter with capture feedback. */
class DjiPhotoDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter, R.string.dji_photo) { fieldConfig, state ->
            DjiWidgetRenderer.photo(context, fieldConfig, state)
        }
    }

    companion object {
        const val TYPE_ID = "dji-photo"
    }
}

/** Video/photo while idle; the existing video field takes over during recording. */
class DjiVideoPhotoDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter, R.string.dji_video_photo) { fieldConfig, state ->
            DjiWidgetRenderer.videoPhoto(context, fieldConfig, state)
        }
    }

    companion object {
        const val TYPE_ID = "dji-video-photo"
    }
}

private const val TAG = "KarooPoC"
