package com.karoopoc

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

data class DjiPalette(
    val appBackground: Int,
    val galleryBackground: Int,
    val previewBackground: Int,
    val widgetBackground: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textOnAccent: Int,
    val batteryLow: Int,
)

object DjiTheme {
    /** Menus stay light, independently of Karoo's in-ride appearance. */
    fun activityContext(base: Context): Context = base.createConfigurationContext(Configuration().apply {
        uiMode = Configuration.UI_MODE_NIGHT_NO
    })

    fun applyActivityTheme(activity: Activity) {
        activity.setTheme(R.style.AppTheme)
    }

    fun appPalette(context: Context): DjiPalette = DjiPalette(
        appBackground = color(context, R.color.dji_bg_canvas_light),
        galleryBackground = color(context, R.color.dji_bg_gallery_light),
        previewBackground = color(context, R.color.dji_bg_preview_light),
        widgetBackground = color(context, R.color.dji_bg_canvas_dark),
        textPrimary = color(context, R.color.dji_text_primary_light),
        textSecondary = color(context, R.color.dji_text_secondary_light),
        textOnAccent = color(context, R.color.dji_text_on_accent),
        batteryLow = color(context, R.color.dji_battery_low_light),
    )

    /** Ride controls keep the same black background and readable labels in every host theme. */
    fun widgetPalette(context: Context): DjiPalette = DjiPalette(
        appBackground = color(context, R.color.dji_bg_canvas_dark),
        galleryBackground = color(context, R.color.dji_bg_gallery_dark),
        previewBackground = color(context, R.color.dji_bg_preview_dark),
        widgetBackground = color(context, R.color.dji_bg_canvas_dark),
        textPrimary = color(context, R.color.dji_text_primary_dark),
        textSecondary = color(context, R.color.dji_text_secondary_dark),
        textOnAccent = color(context, R.color.dji_text_on_accent),
        batteryLow = color(context, R.color.dji_battery_low_dark),
    )

    private fun color(context: Context, resId: Int): Int = context.getColor(resId)
}
