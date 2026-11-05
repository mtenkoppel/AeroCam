package com.karoopoc

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.*
import com.karoopoc.dji.*
import com.karoopoc.widget.DjiWidgetController

internal data class TriggerNumber(val key: String, val label: Int, val default: Int, val min: Int, val max: Int)
internal data class AutomationTrigger(val key: String, val title: Int, val description: Int,
    val numbers: List<TriggerNumber> = emptyList(), val speedKey: String? = null) {
    fun selected(activity: Activity): Boolean {
        val p = DjiSettings.preferences(activity)
        return p.getBoolean("selected_$key", p.getBoolean(key, false))
    }
}
internal object AutomationTriggers {
    val all = listOf(
        AutomationTrigger(DjiSettings.KEY_LAP_HIGHLIGHTS, R.string.highlight_lap, R.string.automation_lap_help),
        AutomationTrigger(DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS, R.string.highlight_peak_power, R.string.automation_power_help,
            listOf(TriggerNumber(DjiSettings.KEY_PEAK_POWER_WATTS, R.string.highlight_peak_power_threshold, 250, 1, 2500))),
        AutomationTrigger(DjiSettings.KEY_MAX_SPEED_HIGHLIGHTS, R.string.highlight_max_speed, R.string.automation_speed_help,
            listOf(TriggerNumber(DjiSettings.KEY_MAX_SPEED_KPH, R.string.highlight_max_speed_threshold, 40, 1, 150))),
        AutomationTrigger(DjiSettings.KEY_CLIMB_SUMMIT_HIGHLIGHTS, R.string.highlight_climb_summit, R.string.automation_summit_help),
        AutomationTrigger(DjiSettings.KEY_PEAK_HEART_RATE_HIGHLIGHTS, R.string.highlight_peak_heart_rate, R.string.automation_hr_help,
            listOf(TriggerNumber(DjiSettings.KEY_PEAK_HEART_RATE_BPM, R.string.highlight_peak_heart_rate_threshold, 165, 1, 250))),
        AutomationTrigger(DjiSettings.KEY_UPHILL_HIGHLIGHTS, R.string.highlight_uphill, R.string.highlight_uphill_summary,
            listOf(TriggerNumber(DjiSettings.KEY_UPHILL_GRADE_PERCENT, R.string.highlight_uphill_grade_threshold, 3, 1, 30),
                TriggerNumber(DjiSettings.KEY_UPHILL_SPEED_KPH, R.string.highlight_uphill_speed_threshold, 20, 1, 80)), DjiSettings.KEY_UPHILL_REQUIRE_SPEED),
        AutomationTrigger(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, R.string.highlight_high_speed_descent, R.string.highlight_descent_summary,
            listOf(TriggerNumber(DjiSettings.KEY_DESCENT_GRADE_PERCENT, R.string.highlight_descent_grade_threshold, -3, -30, -1),
                TriggerNumber(DjiSettings.KEY_DESCENT_SPEED_KPH, R.string.highlight_descent_speed_threshold, 50, 40, 80)), DjiSettings.KEY_DESCENT_REQUIRE_SPEED),
        AutomationTrigger(DjiSettings.KEY_RIDE_BOOKMARKS, R.string.highlight_ride_bookmarks, R.string.ob_bookmarks),
    )
}

/** Same card, controls, theme and row helpers as the other settings sections. */
internal class AutomationSettingsUi(private val activity: Activity, private val ui: SettingsViews) {
    private lateinit var card: LinearLayout
    private var signature = ""
    fun attach(parent: LinearLayout) {
        card = ui.group(parent, activity.getString(R.string.automation_title), R.drawable.ic_widget_highlight)
        refresh()
    }
    fun refresh() {
        val current = DjiSettings.preferences(activity).all.filterKeys { it.startsWith("highlight_") || it.startsWith("automation_") || it.startsWith("selected_") || it == DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED }.toString()
        if (signature == current) return
        signature = current
        while (card.childCount > 1) card.removeViewAt(1)
        val heading = card.getChildAt(0)
        if (heading is TextView) {
            card.removeView(heading)
            card.addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ui.action(activity.getString(R.string.automation_more_information)) { open("help") }.apply {
                    textSize = 12f
                    setCompoundDrawables(null, null, null, null)
                })
            }, 0)
        }
        val settings = DjiSettings.read(activity)
        val master = Switch(activity).apply {
            text = activity.getString(R.string.automation_enable); textSize = 16f; minHeight = ui.dp(48)
            isChecked = settings.autoHighlightsEnabled
            setOnCheckedChangeListener { _, checked ->
                DjiSettings.setBoolean(activity, DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, checked)
                changed(); refresh()
            }
        }
        card.addView(master, ui.fullWidthParams())
        if (!master.isChecked) return
        val choices = RadioGroup(activity)
        val modes = listOf(
            Triple(AutomationMode.HIGHLIGHTS, R.string.automation_highlights, R.string.automation_highlights_summary),
            Triple(AutomationMode.RECORDING, if (settings.autoStop) R.string.automation_recording else R.string.automation_recording_start_only, R.string.automation_recording_summary),
            Triple(AutomationMode.PHOTOS, R.string.automation_photos, R.string.automation_photos_summary),
        )
        modes.forEach { (mode, title, summary) ->
            choices.addView(RadioButton(activity).apply {
                id = View.generateViewId(); text = activity.getString(title); textSize = 16f; minHeight = ui.dp(48)
                isChecked = settings.mode == mode
                setOnClickListener {
                    DjiSettings.preferences(activity).edit().putString(DjiSettings.KEY_AUTOMATION_MODE, mode.name)
                        .putBoolean(DjiSettings.KEY_AUTO_START_RECORDING, mode == AutomationMode.RECORDING).apply()
                    changed(); refresh()
                }
            }, ui.fullWidthParams())
            choices.addView(ui.secondaryText(activity.getString(summary)).apply { setPadding(ui.dp(32), 0, 0, ui.dp(8)) })
        }
        card.addView(choices)
        if (settings.mode == AutomationMode.RECORDING) card.addView(ui.action(activity.getString(R.string.automation_recording_settings)) { open("recording") }, ui.fullWidthParams())
        card.addView(ui.text(activity.getString(R.string.highlight_triggers), 16f), ui.fullWidthParams(ui.dp(12), ui.dp(4)))
        val selected = AutomationTriggers.all.filter { it.selected(activity) }
        if (selected.isEmpty()) card.addView(ui.secondaryText(activity.getString(R.string.automation_empty)), ui.fullWidthParams(0, ui.dp(8)))
        selected.forEach { trigger ->
            card.addView(LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                val prefs = DjiSettings.preferences(activity)
                val label = buildString {
                    append(activity.getString(trigger.title))
                    if (trigger.numbers.isNotEmpty()) append("\n" + trigger.numbers.joinToString(" · ") {
                        "${prefs.getInt(it.key, it.default)}" + when(it.key) {
                            DjiSettings.KEY_PEAK_POWER_WATTS -> " W"
                            DjiSettings.KEY_PEAK_HEART_RATE_BPM -> " bpm"
                            DjiSettings.KEY_UPHILL_GRADE_PERCENT, DjiSettings.KEY_DESCENT_GRADE_PERCENT -> "%"
                            else -> " km/h"
                        }
                    })
                }
                addView(ui.action(label) { open(trigger.key) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(View(activity).apply { setBackgroundColor(0xFFDDDDDD.toInt()) }, LinearLayout.LayoutParams(ui.dp(1), ui.dp(32)))
                addView(Switch(activity).apply {
                    minWidth = ui.dp(56); minHeight = ui.dp(48)
                    contentDescription = activity.getString(trigger.title)
                    isChecked = prefs.getBoolean(trigger.key, false)
                    setOnCheckedChangeListener { _, checked ->
                        prefs.edit().putBoolean("selected_${trigger.key}", true).putBoolean(trigger.key, checked).apply()
                        changed()
                    }
                })
            }, ui.fullWidthParams())
        }
        card.addView(Button(activity).apply {
            text = activity.getString(R.string.automation_add_trigger); isAllCaps = false
            setOnClickListener { open("catalog") }
        }, ui.fullWidthParams(ui.dp(8)))
    }
    private fun changed() { DjiCameraController.cancelAutomation(activity); DjiWidgetController.refresh() }
    private fun open(page: String) { activity.startActivity(Intent(activity, AutomationActivity::class.java).putExtra("page", page)) }
}
