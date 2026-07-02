package com.karoopoc.widget

import android.content.Context
import android.util.Log
import com.karoopoc.dji.DjiSettings
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
        render: (DjiWidgetViewState) -> android.widget.RemoteViews,
    ) {
        Log.i(TAG, "DJI field start type=$typeId size=${config.viewSize} grid=${config.gridSize}")
        emitter.onNext(UpdateGraphicConfig(showHeader = !DjiSettings.hideFieldCaptions(context)))
        val cancel = DjiWidgetController.observe(context) { emitter.updateView(render(it)) }
        emitter.setCancellable(cancel)
    }
}

/** Video recording, timer and highlight control. The ID remains stable for existing profiles. */
class DjiCameraStatusDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter) {
            DjiWidgetRenderer.video(context, config, it)
        }
    }

    companion object {
        const val TYPE_ID = "dji-status"
    }
}

/** Camera connection, Keep Awake and deep-sleep control. */
class DjiPowerDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter) {
            DjiWidgetRenderer.cameraControl(context, config, it)
        }
    }

    companion object {
        const val TYPE_ID = "dji-power"
    }
}

/** Single-purpose photo shutter with capture feedback. */
class DjiPhotoDataType(extension: String) : DjiWidgetDataType(extension, TYPE_ID) {
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        observe(context, config, emitter) {
            DjiWidgetRenderer.photo(context, config, it)
        }
    }

    companion object {
        const val TYPE_ID = "dji-photo"
    }
}

private const val TAG = "KarooPoC"
