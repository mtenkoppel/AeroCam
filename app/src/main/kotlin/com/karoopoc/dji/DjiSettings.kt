package com.karoopoc.dji

import android.content.Context

data class DjiAutomationSettings(
    val autoHighlightsEnabled: Boolean,
    val autoStartRecording: Boolean,
    val highlightCooldownSeconds: Int,
    val lapHighlights: Boolean,
    val peakPowerHighlights: Boolean,
    val peakPowerWatts: Int,
    val maxSpeedHighlights: Boolean,
    val maxSpeedKph: Int,
    val climbSummitHighlights: Boolean,
    val peakHeartRateHighlights: Boolean,
    val peakHeartRateBpm: Int,
    val highSpeedDescentHighlights: Boolean,
    val descentSpeedKph: Int,
    val descentGradePercent: Int,
    val uphillHighlights: Boolean,
    val uphillSpeedKph: Int,
    val uphillGradePercent: Int,
    val rideBookmarks: Boolean,
    val lowBatteryAlert: Boolean,
    val lowStorageAlert: Boolean,
)

object DjiSettings {
    const val PREFERENCES_NAME = "dji_osmo_poc"

    const val KEY_AUTO_HIGHLIGHTS_ENABLED = "auto_highlights_enabled"
    const val KEY_HIGHLIGHT_COOLDOWN_SECONDS = "highlight_cooldown_seconds"
    const val KEY_AUTO_START_RECORDING = "highlight_auto_start_recording"
    const val KEY_LAP_HIGHLIGHTS = "highlight_lap"
    const val KEY_PEAK_POWER_HIGHLIGHTS = "highlight_peak_power"
    const val KEY_PEAK_POWER_WATTS = "highlight_peak_power_watts"
    const val KEY_MAX_SPEED_HIGHLIGHTS = "highlight_max_speed"
    const val KEY_MAX_SPEED_KPH = "highlight_max_speed_kph"
    const val KEY_CLIMB_SUMMIT_HIGHLIGHTS = "highlight_climb_summit"
    const val KEY_PEAK_HEART_RATE_HIGHLIGHTS = "highlight_peak_heart_rate"
    const val KEY_PEAK_HEART_RATE_BPM = "highlight_peak_heart_rate_bpm"
    const val KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS = "highlight_high_speed_descent"
    const val KEY_DESCENT_SPEED_KPH = "highlight_descent_speed_kph"
    const val KEY_DESCENT_GRADE_PERCENT = "highlight_descent_grade_percent"
    const val KEY_UPHILL_HIGHLIGHTS = "highlight_uphill"
    const val KEY_UPHILL_SPEED_KPH = "highlight_uphill_speed_kph"
    const val KEY_UPHILL_GRADE_PERCENT = "highlight_uphill_grade_percent"
    const val KEY_RIDE_BOOKMARKS = "highlight_ride_bookmarks"
    private const val KEY_RIDE_MARKER_DEFAULT_MIGRATED = "ride_marker_default_migrated_v1"
    const val KEY_LOW_BATTERY_ALERT = "alert_low_battery"
    const val KEY_LOW_STORAGE_ALERT = "alert_low_storage"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_KEEP_AWAKE_ENABLED = "keep_awake_enabled"
    const val KEY_KEEP_AWAKE_INTERVAL_SECONDS = "keep_awake_interval_seconds"
    const val KEY_HIDE_FIELD_CAPTIONS = "hide_field_captions"

    const val DEFAULT_PEAK_POWER_WATTS = 250
    const val DEFAULT_MAX_SPEED_KPH = 40
    const val DEFAULT_PEAK_HEART_RATE_BPM = 165
    const val DEFAULT_DESCENT_SPEED_KPH = 50
    const val DEFAULT_DESCENT_GRADE_PERCENT = -3
    const val MIN_DESCENT_GRADE_PERCENT = -30
    const val MAX_DESCENT_GRADE_PERCENT = -1
    const val DEFAULT_UPHILL_SPEED_KPH = 20
    const val DEFAULT_UPHILL_GRADE_PERCENT = 3
    const val LOW_BATTERY_PERCENT = 20
    const val LOW_STORAGE_MB = 5 * 1_024L

    fun read(context: Context): DjiAutomationSettings {
        val prefs = preferences(context)
        // Remove only the retired option; preserve pairing and all other settings.
        if (prefs.contains("alert_camera_actions")) {
            prefs.edit().remove("alert_camera_actions").apply()
        }
        return DjiAutomationSettings(
            autoHighlightsEnabled = prefs.getBoolean(KEY_AUTO_HIGHLIGHTS_ENABLED, true),
            autoStartRecording = prefs.getBoolean(KEY_AUTO_START_RECORDING, false),
            highlightCooldownSeconds = highlightCooldownSeconds(context),
            lapHighlights = prefs.getBoolean(KEY_LAP_HIGHLIGHTS, false),
            peakPowerHighlights = prefs.getBoolean(KEY_PEAK_POWER_HIGHLIGHTS, false),
            peakPowerWatts = prefs.getInt(KEY_PEAK_POWER_WATTS, DEFAULT_PEAK_POWER_WATTS)
                .coerceAtLeast(1),
            maxSpeedHighlights = prefs.getBoolean(KEY_MAX_SPEED_HIGHLIGHTS, false),
            maxSpeedKph = prefs.getInt(KEY_MAX_SPEED_KPH, DEFAULT_MAX_SPEED_KPH)
                .coerceAtLeast(1),
            climbSummitHighlights = prefs.getBoolean(KEY_CLIMB_SUMMIT_HIGHLIGHTS, false),
            peakHeartRateHighlights = prefs.getBoolean(KEY_PEAK_HEART_RATE_HIGHLIGHTS, false),
            peakHeartRateBpm = prefs.getInt(KEY_PEAK_HEART_RATE_BPM, DEFAULT_PEAK_HEART_RATE_BPM)
                .coerceAtLeast(1),
            highSpeedDescentHighlights = prefs.getBoolean(KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, false),
            descentSpeedKph = prefs.getInt(KEY_DESCENT_SPEED_KPH, DEFAULT_DESCENT_SPEED_KPH)
                .coerceIn(40, 80),
            descentGradePercent = prefs.getInt(KEY_DESCENT_GRADE_PERCENT, DEFAULT_DESCENT_GRADE_PERCENT)
                .coerceIn(MIN_DESCENT_GRADE_PERCENT, MAX_DESCENT_GRADE_PERCENT),
            uphillHighlights = prefs.getBoolean(KEY_UPHILL_HIGHLIGHTS, false),
            uphillSpeedKph = prefs.getInt(KEY_UPHILL_SPEED_KPH, DEFAULT_UPHILL_SPEED_KPH).coerceIn(1, 80),
            uphillGradePercent = prefs.getInt(KEY_UPHILL_GRADE_PERCENT, DEFAULT_UPHILL_GRADE_PERCENT).coerceIn(1, 30),
            rideBookmarks = prefs.getBoolean(KEY_RIDE_BOOKMARKS, false),
            lowBatteryAlert = prefs.getBoolean(KEY_LOW_BATTERY_ALERT, true),
            lowStorageAlert = prefs.getBoolean(KEY_LOW_STORAGE_ALERT, false),
        )
    }

    fun highlightCooldownSeconds(context: Context): Int = HighlightCooldown.normalize(
        preferences(context).getInt(KEY_HIGHLIGHT_COOLDOWN_SECONDS, HighlightCooldown.DEFAULT_SECONDS),
    )

    fun setBoolean(context: Context, key: String, value: Boolean) {
        preferences(context).edit().putBoolean(key, value).apply()
    }

    fun setInt(context: Context, key: String, value: Int) {
        preferences(context).edit().putInt(key, value).apply()
    }

    fun hideFieldCaptions(context: Context): Boolean =
        preferences(context).getBoolean(KEY_HIDE_FIELD_CAPTIONS, false)

    fun resetAll(context: Context) {
        preferences(context).edit().clear()
            .putBoolean(KEY_RIDE_BOOKMARKS, false)
            .putBoolean(KEY_RIDE_MARKER_DEFAULT_MIGRATED, true)
            .apply()
    }

    @Synchronized
    fun preferences(context: Context): android.content.SharedPreferences {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_RIDE_MARKER_DEFAULT_MIGRATED, false)) {
            val editor = prefs.edit()
            if (!prefs.contains(KEY_RIDE_BOOKMARKS)) {
                // Existing installs used ON when the key was absent. Android's install record
                // also identifies upgrades with otherwise empty preferences. Onboarding and
                // confirmed-pairing evidence covers restored older app data.
                @Suppress("DEPRECATION")
                val install = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                val existingInstall = install.lastUpdateTime > install.firstInstallTime ||
                    prefs.contains("onboarding_completed_v1") || prefs.contains("onboarding_draft_v1") ||
                    prefs.getBoolean("protocol_connected_once", false)
                editor.putBoolean(KEY_RIDE_BOOKMARKS, existingInstall)
            }
            editor.putBoolean(KEY_RIDE_MARKER_DEFAULT_MIGRATED, true).apply()
        }
        return prefs
    }
}
