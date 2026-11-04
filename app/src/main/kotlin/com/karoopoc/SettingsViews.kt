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
    private data class NumericBinding(val view: EditText, val key: String, val default: Int)
    private val toggles = mutableListOf<ToggleBinding>()
    private val numericBindings = mutableListOf<NumericBinding>()
    private var cooldownSpinner: Spinner? = null
    private var refreshing = false
    private lateinit var autoHighlightsContent: LinearLayout
    private lateinit var peakPowerThresholdRow: LinearLayout
    private lateinit var maxSpeedThresholdRow: LinearLayout
    private lateinit var peakHeartRateThresholdRow: LinearLayout
    private lateinit var descentThresholdRows: LinearLayout
    private lateinit var uphillThresholdRows: LinearLayout
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
            numericBindings.filter { !it.view.hasFocus() }.forEach {
                val value = prefs.getInt(it.key, it.default).toString()
                if (it.view.text.toString() != value) it.view.setText(value)
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
    fun automation(content: LinearLayout) {
        val automationGroup = group(content, getString(R.string.automatic_highlights), R.drawable.ic_widget_highlight)
        val autoHighlightsSwitch = settingsSwitch(
            automationGroup,
            getString(R.string.automatic_highlights_enabled),
            DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED,
            true,
            summary = getString(R.string.automatic_highlights_summary),
        ) { checked ->
            autoHighlightsContent.visibility = if (checked) View.VISIBLE else View.GONE
        }
        autoHighlightsContent = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (autoHighlightsSwitch.isChecked) View.VISIBLE else View.GONE
        }
        automationGroup.addView(
            autoHighlightsContent,
            fullWidthParams(),
        )

        settingsSwitch(
            autoHighlightsContent,
            getString(R.string.highlight_auto_start_recording),
            DjiSettings.KEY_AUTO_START_RECORDING,
            false,
            summary = getString(R.string.highlight_auto_start_recording_summary),
        )

        val triggerGroup = subgroup(autoHighlightsContent, getString(R.string.highlight_triggers))
        settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_lap),
            DjiSettings.KEY_LAP_HIGHLIGHTS,
            false,
        )

        val peakPowerSwitch = settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_peak_power),
            DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS,
            false,
        ) { checked ->
            peakPowerThresholdRow.visibility = if (checked) View.VISIBLE else View.GONE
        }
        peakPowerThresholdRow = numberRow(
            getString(R.string.highlight_peak_power_threshold),
            DjiSettings.KEY_PEAK_POWER_WATTS,
            DjiSettings.DEFAULT_PEAK_POWER_WATTS,
            300,
            1_000,
        )
        peakPowerThresholdRow.visibility = if (peakPowerSwitch.isChecked) View.VISIBLE else View.GONE
        triggerGroup.addView(peakPowerThresholdRow, fullWidthParams(bottomMargin = dp(14)))

        val maxSpeedSwitch = settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_max_speed),
            DjiSettings.KEY_MAX_SPEED_HIGHLIGHTS,
            false,
        ) { checked ->
            maxSpeedThresholdRow.visibility = if (checked) View.VISIBLE else View.GONE
        }
        maxSpeedThresholdRow = numberRow(
            getString(R.string.highlight_max_speed_threshold),
            DjiSettings.KEY_MAX_SPEED_KPH,
            DjiSettings.DEFAULT_MAX_SPEED_KPH,
            30,
            80,
        )
        maxSpeedThresholdRow.visibility = if (maxSpeedSwitch.isChecked) View.VISIBLE else View.GONE
        triggerGroup.addView(maxSpeedThresholdRow, fullWidthParams(bottomMargin = dp(14)))

        settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_climb_summit),
            DjiSettings.KEY_CLIMB_SUMMIT_HIGHLIGHTS,
            false,
        )
        val peakHeartRateSwitch = settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_peak_heart_rate),
            DjiSettings.KEY_PEAK_HEART_RATE_HIGHLIGHTS,
            false,
        ) { checked ->
            peakHeartRateThresholdRow.visibility = if (checked) View.VISIBLE else View.GONE
        }
        peakHeartRateThresholdRow = numberRow(
            getString(R.string.highlight_peak_heart_rate_threshold),
            DjiSettings.KEY_PEAK_HEART_RATE_BPM,
            DjiSettings.DEFAULT_PEAK_HEART_RATE_BPM,
            120,
            220,
        )
        peakHeartRateThresholdRow.visibility = if (peakHeartRateSwitch.isChecked) View.VISIBLE else View.GONE
        triggerGroup.addView(peakHeartRateThresholdRow, fullWidthParams(bottomMargin = dp(14)))
        val uphillSwitch = settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_uphill),
            DjiSettings.KEY_UPHILL_HIGHLIGHTS,
            false,
        ) { checked ->
            uphillThresholdRows.visibility = if (checked) View.VISIBLE else View.GONE
        }
        uphillThresholdRows = column().apply {
            visibility = if (uphillSwitch.isChecked) View.VISIBLE else View.GONE
            addView(secondaryText(getString(R.string.highlight_uphill_summary)), fullWidthParams(bottomMargin = dp(8)))
            val speedRow = numberRow(getString(R.string.highlight_uphill_speed_threshold),
                DjiSettings.KEY_UPHILL_SPEED_KPH, DjiSettings.DEFAULT_UPHILL_SPEED_KPH, 1, 80)
            settingsSwitch(this, getString(R.string.highlight_require_speed), DjiSettings.KEY_UPHILL_REQUIRE_SPEED, true) { checked ->
                speedRow.visibility = if (checked) View.VISIBLE else View.GONE
            }
            speedRow.visibility = if (DjiSettings.read(activity).uphillRequireSpeed) View.VISIBLE else View.GONE
            addView(speedRow, fullWidthParams(bottomMargin = dp(8)))
            addView(numberRow(getString(R.string.highlight_uphill_grade_threshold),
                DjiSettings.KEY_UPHILL_GRADE_PERCENT, DjiSettings.DEFAULT_UPHILL_GRADE_PERCENT, 1, 30),
                fullWidthParams(bottomMargin = dp(14)))
        }
        triggerGroup.addView(uphillThresholdRows, fullWidthParams())
        val descentSwitch = settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_high_speed_descent),
            DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS,
            false,
        ) { checked ->
            descentThresholdRows.visibility = if (checked) View.VISIBLE else View.GONE
        }
        descentThresholdRows = column().apply {
            visibility = if (descentSwitch.isChecked) View.VISIBLE else View.GONE
            addView(secondaryText(getString(R.string.highlight_descent_summary)), fullWidthParams(bottomMargin = dp(8)))
            val speedRow = numberRow(getString(R.string.highlight_descent_speed_threshold),
                DjiSettings.KEY_DESCENT_SPEED_KPH, DjiSettings.DEFAULT_DESCENT_SPEED_KPH, 40, 80)
            settingsSwitch(this, getString(R.string.highlight_require_speed), DjiSettings.KEY_DESCENT_REQUIRE_SPEED, true) { checked ->
                speedRow.visibility = if (checked) View.VISIBLE else View.GONE
            }
            speedRow.visibility = if (DjiSettings.read(activity).descentRequireSpeed) View.VISIBLE else View.GONE
            addView(speedRow, fullWidthParams(bottomMargin = dp(8)))
            addView(numberRow(
                getString(R.string.highlight_descent_grade_threshold),
                DjiSettings.KEY_DESCENT_GRADE_PERCENT,
                DjiSettings.DEFAULT_DESCENT_GRADE_PERCENT,
                DjiSettings.MIN_DESCENT_GRADE_PERCENT,
                DjiSettings.MAX_DESCENT_GRADE_PERCENT,
            ), fullWidthParams(bottomMargin = dp(14)))
        }
        triggerGroup.addView(descentThresholdRows, fullWidthParams())
        settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_ride_bookmarks),
            DjiSettings.KEY_RIDE_BOOKMARKS,
            false,
            summary = getString(R.string.ob_bookmarks),
        )

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

    private fun labelText(value: String) = text(value, 14f)
    private fun numberRow(
        label: String,
        key: String,
        default: Int,
        minimum: Int,
        maximum: Int,
    ): LinearLayout {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or if (minimum < 0) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setTextColor(palette.textPrimary)
            setText(DjiSettings.preferences(activity).getInt(key, default).toString())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveNumericSetting(this, key, default, minimum, maximum)
                    clearFocus()
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveNumericSetting(this, key, default, minimum, maximum)
                }
            }
        }

        numericBindings += NumericBinding(input, key, default)
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(
                labelText(label),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(input, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun saveNumericSetting(
        input: EditText,
        key: String,
        default: Int,
        minimum: Int,
        maximum: Int,
    ) {
        val value = input.text.toString().toIntOrNull()?.coerceIn(minimum, maximum) ?: default
        input.setText(value.toString())
        DjiSettings.setInt(activity, key, value)
    }

}
