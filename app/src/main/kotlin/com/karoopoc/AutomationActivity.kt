package com.karoopoc

import android.app.Activity
import android.app.ActivityOptions
import android.transition.Slide
import android.view.Window
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import com.karoopoc.dji.*
import com.karoopoc.widget.DjiWidgetController

/** Draft edits commit only through the existing Karoo forward tab; Back discards. */
class AutomationActivity : Activity() {
    private lateinit var ui: SettingsViews
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(DjiTheme.activityContext(newBase))
    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        super.onCreate(savedInstanceState)
        val edge = if (intent.getBooleanExtra("drill_down", false)) Gravity.END else Gravity.BOTTOM
        window.enterTransition = Slide(edge)
        window.returnTransition = Slide(edge)
        ui = SettingsViews(this)
        render()
    }
    override fun onResume() {
        super.onResume()
        if (::ui.isInitialized && intent.getStringExtra("page") == "catalog") render()
    }
    private fun render() {
        val content = ui.column().apply { setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12)) }
        val page = intent.getStringExtra("page") ?: "help"
        val prefs = DjiSettings.preferences(this)
        var save: (() -> Unit)? = null
        var actionLabel = getString(R.string.automation_save)
        fun title(id: Int) { content.addView(ui.article("# " + getString(id))) }
        fun description(id: Int) { content.addView(ui.article(getString(id))) }
        when (page) {
            "help" -> {
                title(R.string.automation_about)
                description(R.string.automation_help)
            }
            "catalog" -> {
                title(R.string.automation_add_trigger)
                description(R.string.automation_catalog_help)
                AutomationTriggers.all.filterNot { it.selected(this) }.forEach { trigger ->
                    val label = android.text.SpannableStringBuilder(getString(trigger.title)).apply {
                        setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
                        setSpan(android.text.style.AbsoluteSizeSpan(17, true), 0, length, 0)
                        append("\n")
                        val start = length
                        append(getString(trigger.description))
                        setSpan(android.text.style.ForegroundColorSpan(ui.palette.textSecondary), start, length, 0)
                    }
                    content.addView(ui.action(label) {
                        startActivityForResult(Intent(this, AutomationActivity::class.java)
                            .putExtra("page", trigger.key).putExtra("drill_down", true), ADD_TRIGGER,
                            ActivityOptions.makeSceneTransitionAnimation(this).toBundle())
                    }.apply { textSize = 15f; typeface = android.graphics.Typeface.DEFAULT; setLineSpacing(0f, 1.12f) },
                        ui.fullWidthParams(0, ui.dp(8)))
                }
            }
            "recording" -> {
                title(R.string.automation_recording_settings)
                val settings = DjiSettings.read(this)
                val stop = Switch(this).apply { text = getString(R.string.automation_stop_automatically); textSize = 16f; minHeight = ui.dp(48); isChecked = settings.autoStop }
                content.addView(stop, ui.fullWidthParams())
                val seconds = number(content, TriggerNumber(DjiSettings.KEY_STOP_SECONDS, R.string.automation_stop_seconds, 60, 10, 3600))
                seconds.isEnabled = stop.isChecked
                stop.setOnCheckedChangeListener { _, checked -> seconds.isEnabled = checked }
                val highlights = CheckBox(this).apply {
                    text = getString(R.string.automation_add_highlights); textSize = 16f; minHeight = ui.dp(48); isChecked = settings.addAutomaticHighlights
                }
                content.addView(highlights, ui.fullWidthParams())
                save = {
                    val value = seconds.text.toString().toIntOrNull()
                    if (value == null || value !in 10..3600) seconds.error = getString(R.string.automation_invalid_number, 10, 3600)
                    else { prefs.edit().putBoolean(DjiSettings.KEY_AUTO_STOP, stop.isChecked).putInt(DjiSettings.KEY_STOP_SECONDS, value)
                        .putBoolean(DjiSettings.KEY_ADD_HIGHLIGHTS, highlights.isChecked).apply(); committed() }
                }
            }
            else -> {
                val trigger = AutomationTriggers.all.firstOrNull { it.key == page } ?: run { finish(); return }
                val existing = trigger.selected(this)
                title(trigger.title)
                description(trigger.description)
                description(if (trigger.key == DjiSettings.KEY_RIDE_BOOKMARKS) R.string.automation_boundary_help else R.string.automation_trigger_detail_help)
                val fields = trigger.numbers.associateWith { number(content, it) }
                val speed = trigger.speedKey?.let { key ->
                    Switch(this).apply {
                        text = getString(R.string.highlight_require_speed); textSize = 16f; minHeight = ui.dp(48)
                        isChecked = prefs.getBoolean(key, true)
                        setOnCheckedChangeListener { _, checked -> fields.entries.lastOrNull()?.value?.isEnabled = checked }
                        content.addView(this, ui.fullWidthParams())
                        fields.entries.lastOrNull()?.value?.isEnabled = isChecked
                    }
                }
                if (existing) content.addView(ui.action(getString(R.string.automation_remove_trigger)) {
                    prefs.edit().putBoolean("selected_${trigger.key}", false).putBoolean(trigger.key, false).apply()
                    committed()
                }.apply {
                    gravity = Gravity.CENTER
                    val icon = getDrawable(android.R.drawable.ic_menu_delete)!!.mutate()
                    icon.setTint(ui.palette.textSecondary); icon.setBounds(0, 0, ui.dp(20), ui.dp(20))
                    setCompoundDrawablesRelative(icon, null, null, null)
                }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = ui.dp(24) })
                actionLabel = getString(if (existing) R.string.automation_save else R.string.automation_add)
                save = {
                    var valid = true
                    val values = fields.map { (spec, input) ->
                        val value = input.text.toString().toIntOrNull()
                        if (value == null || value !in spec.min..spec.max) {
                            input.error = getString(R.string.automation_invalid_number, spec.min, spec.max); valid = false
                        }
                        spec.key to (value ?: spec.default)
                    }
                    if (valid) {
                        val editor = prefs.edit().putBoolean("selected_${trigger.key}", true)
                        if (!existing) editor.putBoolean(trigger.key, true)
                        values.forEach { (key, value) -> editor.putInt(key, value) }
                        if (trigger.speedKey != null) editor.putBoolean(trigger.speedKey, speed!!.isChecked)
                        editor.apply(); committed()
                    }
                }
            }
        }
        val root = KarooNavigation.wrap(this, ui.scroll(content)) as FrameLayout
        save?.let { commit -> root.addView(KarooNavigation.forwardButton(this, actionLabel, commit), FrameLayout.LayoutParams(-2, KarooNavigation.buttonSize(this), Gravity.RIGHT or Gravity.BOTTOM).apply { bottomMargin = KarooNavigation.bottomInset(this@AutomationActivity) }) }
        setContentView(root)
    }
    private fun number(parent: LinearLayout, spec: TriggerNumber): EditText = ui.numberInput(
        parent, getString(spec.label), DjiSettings.preferences(this).getInt(spec.key, spec.default), spec.min < 0,
    )
    private fun committed() {
        DjiCameraController.cancelAutomation(this)
        DjiWidgetController.refresh()
        setResult(RESULT_OK)
        finishAfterTransition()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ADD_TRIGGER && resultCode == RESULT_OK) finishAfterTransition()
    }
    companion object {
        private const val ADD_TRIGGER = 1
        fun open(activity: Activity, page: String) {
            activity.startActivity(Intent(activity, AutomationActivity::class.java).putExtra("page", page),
                ActivityOptions.makeSceneTransitionAnimation(activity).toBundle())
        }
    }
}
