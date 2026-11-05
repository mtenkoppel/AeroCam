package com.karoopoc

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.HighlightCooldown
import com.karoopoc.dji.DjiSettings
import com.karoopoc.widget.DjiWidgetController

/** Standard Android controls; only their surrounding layout belongs to AeroCam. */
internal class SettingsViews(val activity: Activity) {
    val palette = DjiTheme.appPalette(activity)
    private data class ToggleBinding(val view: Switch, val key: String, val default: Boolean, val changed: ((Boolean) -> Unit)?)
    private val toggles = mutableListOf<ToggleBinding>()
    private var cooldownSpinner: Spinner? = null
    private var refreshing = false
    private fun getString(id: Int) = activity.getString(id)
    fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()
    fun fullWidthParams(topMargin: Int = 0, bottomMargin: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        this.topMargin = topMargin
        this.bottomMargin = bottomMargin
    }
    fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    fun text(value: String, size: Float = 14f, bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(palette.textPrimary)
        if (bold) setTypeface(Typeface.DEFAULT_BOLD)
    }
    fun secondaryText(value: String) = text(value, 13f).apply { setTextColor(palette.textSecondary) }
    fun scroll(content: LinearLayout, viewId: Int = View.NO_ID) = ScrollView(activity).apply {
        id = viewId
        isFillViewport = true
        setBackgroundColor(palette.appBackground)
        addView(content, android.view.ViewGroup.LayoutParams(-1, -2))
    }
    fun cardBackground() = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(palette.previewBackground)
        setStroke(dp(1), if (palette.appBackground == Color.BLACK) 0xFF303030.toInt() else 0xFFDDDDDD.toInt())
    }
    fun group(parent: LinearLayout, title: String, icon: Int? = null): LinearLayout {
        val card = column().apply {
            background = cardBackground()
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        card.addView(text(title, 18f, true).apply {
            if (icon != null) {
                val drawable = activity.getDrawable(icon)!!.mutate()
                drawable.setTint(palette.textPrimary)
                drawable.setBounds(0, 0, dp(18), dp(18))
                setCompoundDrawables(drawable, null, null, null)
                compoundDrawablePadding = dp(6)
            }
        }, fullWidthParams(bottomMargin = dp(8)))
        parent.addView(card, fullWidthParams(bottomMargin = dp(12)))
        return card
    }
    private fun subgroup(parent: LinearLayout, title: String): LinearLayout = column().also {
        it.addView(secondaryText(title.uppercase()), fullWidthParams(topMargin = dp(12), bottomMargin = dp(4)))
        parent.addView(it, fullWidthParams())
    }
    fun settingsSwitch(parent: LinearLayout, label: String, key: String, default: Boolean,
        summary: String? = null, onChanged: ((Boolean) -> Unit)? = null): Switch {
        val switch = Switch(activity).apply {
            text = label
            textSize = 16f
            setTextColor(palette.textPrimary)
            switchPadding = dp(10)
            minHeight = dp(48)
            isChecked = DjiSettings.preferences(activity).getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                if (!refreshing) {
                    if (key == DjiSettings.KEY_KEEP_AWAKE_ENABLED) DjiCameraController.setKeepAwake(activity, checked)
                    else DjiSettings.setBoolean(activity, key, checked)
                    onChanged?.invoke(checked)
                }
            }
        }
        toggles += ToggleBinding(switch, key, default, onChanged)
        parent.addView(switch, fullWidthParams())
        if (summary != null) parent.addView(secondaryText(summary), fullWidthParams(bottomMargin = dp(8)))
        return switch
    }
    fun refresh() {
        automationCard?.refresh()
        refreshing = true
        try {
            cooldownSpinner?.setSelection(
                HighlightCooldown.OPTIONS_SECONDS.indexOf(DjiSettings.highlightCooldownSeconds(activity)), false,
            )
            val prefs = DjiSettings.preferences(activity)
            toggles.forEach { binding ->
                val checked = prefs.getBoolean(binding.key, binding.default)
                if (binding.view.isChecked != checked) {
                    binding.view.isChecked = checked
                    binding.changed?.invoke(checked)
                }
            }
        } finally { refreshing = false }
    }
    fun action(label: CharSequence, action: () -> Unit): Button = Button(activity, null, android.R.attr.borderlessButtonStyle).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        minHeight = dp(48)
        setPadding(0, dp(4), 0, dp(4))
        val chevron = activity.getDrawable(R.drawable.ic_chevron_right)!!.mutate()
        chevron.setTint(palette.textSecondary)
        chevron.setBounds(0, 0, dp(20), dp(20))
        setCompoundDrawablesRelative(null, null, chevron, null)
        setOnClickListener { activity.currentFocus?.clearFocus(); action() }
    }
    fun standby(parent: LinearLayout) {
        val card = group(parent, getString(R.string.prevent_standby), R.drawable.ic_coffee)
        settingsSwitch(card, getString(R.string.keep_camera_awake), DjiSettings.KEY_KEEP_AWAKE_ENABLED, false,
            getString(R.string.prevent_standby_summary))
        card.addView(action(getString(R.string.recommended_camera_setup)) {
            HelpActivity.open(activity, "recommended-camera-setup.md")
        }, fullWidthParams())
    }
    private var automationCard: AutomationSettingsUi? = null
    fun automation(content: LinearLayout) {
        automationCard = AutomationSettingsUi(activity, this).also { it.attach(content) }
    }
    fun alerts(parent: LinearLayout) {
        val card = group(parent, getString(R.string.alert_settings))
        settingsSwitch(card, getString(R.string.alert_low_battery_setting), DjiSettings.KEY_LOW_BATTERY_ALERT, true)
        settingsSwitch(card, getString(R.string.alert_low_storage_setting), DjiSettings.KEY_LOW_STORAGE_ALERT, false)
    }
    fun style(parent: LinearLayout) {
        val card = group(parent, getString(R.string.theme_mode))
        settingsSwitch(card, getString(R.string.hide_field_captions), DjiSettings.KEY_HIDE_FIELD_CAPTIONS, false,
            getString(R.string.captions_summary)) { DjiWidgetController.refresh() }
    }
    fun standbyTime(parent: LinearLayout) {
        parent.addView(action(getString(R.string.ob_timer_title)) {
            val times = listOf(60, 120, 300)
            val interval = DjiSettings.preferences(activity).getInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, 270)
            val selected = times.indexOf(interval + 30).coerceAtLeast(0)
            val picker = Spinner(activity).apply {
                contentDescription = getString(R.string.ob_timer_title)
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                    times.map { activity.getString(R.string.ob_minutes, it / 60) })
                setSelection(selected)
            }
            val body = column().apply {
                setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(picker)
                addView(secondaryText(getString(R.string.ob_timer_body)), fullWidthParams(topMargin = dp(8)))
            }
            android.app.AlertDialog.Builder(activity).setTitle(R.string.ob_timer_title).setView(body)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DjiCameraController.setKeepAwakeInterval(activity, OnboardingPolicy.intervalSeconds(times[picker.selectedItemPosition]))
                }.setNegativeButton(android.R.string.cancel, null).show()
        }, fullWidthParams())
    }

    fun cooldown(parent: LinearLayout) {
        parent.addView(action(getString(R.string.highlight_cooldown)) {
            val editor = cooldownRow().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
            android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.highlight_cooldown)
                .setView(editor)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener { cooldownSpinner = null }
                .show()
        }, fullWidthParams())
    }
    private fun cooldownRow(): LinearLayout = column().apply {
        val options = HighlightCooldown.OPTIONS_SECONDS
        val spinner = Spinner(activity).apply {
            contentDescription = getString(R.string.highlight_cooldown)
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item,
                options.map { seconds ->
                    if (seconds == 0) getString(R.string.highlight_cooldown_off)
                    else activity.getString(R.string.highlight_cooldown_seconds, seconds)
                })
            setSelection(options.indexOf(DjiSettings.highlightCooldownSeconds(activity)), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val seconds = options[position]
                    if (!refreshing && seconds != DjiSettings.highlightCooldownSeconds(activity)) {
                        DjiSettings.setInt(activity, DjiSettings.KEY_HIGHLIGHT_COOLDOWN_SECONDS, seconds)
                    }
                }
            }
        }
        cooldownSpinner = spinner
        addView(spinner, fullWidthParams())
        addView(secondaryText(getString(R.string.highlight_cooldown_summary)), fullWidthParams(topMargin = dp(8)))
    }

    /** Native numeric row shared by settings detail pages; persistence belongs to their Save action. */
    fun numberInput(parent: LinearLayout, label: String, value: Int, signed: Boolean = false): EditText {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setTextColor(palette.textPrimary)
            setText(value.toString())
            contentDescription = label
        }
        parent.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(label), LinearLayout.LayoutParams(0, -2, 1f))
            addView(input, LinearLayout.LayoutParams(dp(72), -2))
        }, fullWidthParams())
        return input
    }
}
