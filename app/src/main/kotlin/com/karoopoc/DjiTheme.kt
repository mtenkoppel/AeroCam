package com.karoopoc

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import com.karoopoc.dji.DjiSettings

enum class DjiThemeMode(val preferenceValue: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromPreference(value: String?): DjiThemeMode = entries.firstOrNull {
            it.preferenceValue == value
        } ?: SYSTEM
    }
}

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
    fun widgetBackgroundResource(context: Context): Int = if (isLight(context)) {
        R.drawable.widget_root_light
    } else {
        R.drawable.widget_root_dark
    }

    fun readMode(context: Context): DjiThemeMode = DjiThemeMode.fromPreference(
        DjiSettings.preferences(context).getString(DjiSettings.KEY_THEME_MODE, DjiThemeMode.SYSTEM.preferenceValue),
    )

    fun writeMode(context: Context, mode: DjiThemeMode) {
        DjiSettings.preferences(context).edit()
            .putString(DjiSettings.KEY_THEME_MODE, mode.preferenceValue)
            .apply()
    }

    fun applyActivityTheme(activity: Activity) {
        // Read from the base context before accessing the activity's resources: Android
        // only permits a configuration override before those resources are created.
        val context = activity.baseContext
        val light = isLight(context)
        if (readMode(context) != DjiThemeMode.SYSTEM) {
            activity.applyOverrideConfiguration(Configuration().apply {
                uiMode = if (light) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            })
        }
        activity.setTheme(if (light) R.style.AppTheme_Light else R.style.AppTheme_Dark)
    }

    fun palette(context: Context): DjiPalette {
        return if (isLight(context)) {
            DjiPalette(
                appBackground = color(context, R.color.dji_bg_canvas_light),
                galleryBackground = color(context, R.color.dji_bg_gallery_light),
                previewBackground = color(context, R.color.dji_bg_preview_light),
                widgetBackground = color(context, R.color.dji_bg_canvas_light),
                textPrimary = color(context, R.color.dji_text_primary_light),
                textSecondary = color(context, R.color.dji_text_secondary_light),
                textOnAccent = color(context, R.color.dji_text_on_accent),
                batteryLow = color(context, R.color.dji_battery_low_light),
            )
        } else {
            DjiPalette(
                appBackground = color(context, R.color.dji_bg_canvas_dark),
                galleryBackground = color(context, R.color.dji_bg_gallery_dark),
                previewBackground = color(context, R.color.dji_bg_preview_dark),
                widgetBackground = color(context, R.color.dji_bg_canvas_dark),
                textPrimary = color(context, R.color.dji_text_primary_dark),
                textSecondary = color(context, R.color.dji_text_secondary_dark),
                textOnAccent = color(context, R.color.dji_text_on_accent),
                batteryLow = color(context, R.color.dji_battery_low_dark),
            )
        }
    }

    internal fun isLight(context: Context): Boolean {
        return when (readMode(context)) {
            DjiThemeMode.LIGHT -> true
            DjiThemeMode.DARK -> false
            DjiThemeMode.SYSTEM -> {
                val mask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                mask != Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun color(context: Context, resId: Int): Int = context.getColor(resId)
}
