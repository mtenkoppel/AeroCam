package com.karoopoc.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class DjiWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "DJI widget action=${intent.action}")
        vibrate(context)
        when (intent.action) {
            ACTION_CONNECT -> DjiWidgetController.connect(context)
            ACTION_REFRESH_VIDEO_SETTINGS -> DjiWidgetController.refreshVideoSettings(context)
            ACTION_PHOTO -> DjiWidgetController.triggerPhoto(context)
            ACTION_RECORD_TOGGLE -> DjiWidgetController.toggleRecording(context)
            ACTION_HIGHLIGHT -> DjiWidgetController.addHighlight(context)
            ACTION_KEEP_AWAKE_TOGGLE -> DjiWidgetController.toggleKeepAwake(context)
            ACTION_SLEEP_REQUEST -> DjiWidgetController.requestSleep()
            ACTION_SLEEP_CONFIRM -> DjiWidgetController.confirmSleep(context)
            ACTION_SLEEP_CANCEL -> DjiWidgetController.cancelSleep()
        }
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45L)
        }
    }

    companion object {
        private const val TAG = "KarooPoC"
        const val ACTION_CONNECT = "com.karoopoc.widget.CONNECT"
        const val ACTION_REFRESH_VIDEO_SETTINGS = "com.karoopoc.widget.REFRESH_VIDEO_SETTINGS"
        const val ACTION_PHOTO = "com.karoopoc.widget.PHOTO"
        const val ACTION_RECORD_TOGGLE = "com.karoopoc.widget.RECORD_TOGGLE"
        const val ACTION_HIGHLIGHT = "com.karoopoc.widget.HIGHLIGHT"
        const val ACTION_KEEP_AWAKE_TOGGLE = "com.karoopoc.widget.KEEP_AWAKE_TOGGLE"
        const val ACTION_SLEEP_REQUEST = "com.karoopoc.widget.SLEEP_REQUEST"
        const val ACTION_SLEEP_CONFIRM = "com.karoopoc.widget.SLEEP_CONFIRM"
        const val ACTION_SLEEP_CANCEL = "com.karoopoc.widget.SLEEP_CANCEL"
    }
}
