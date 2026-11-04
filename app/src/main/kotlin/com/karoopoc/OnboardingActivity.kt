package com.karoopoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiSettings
import com.karoopoc.widget.DjiWidgetController
import org.json.JSONArray
import org.json.JSONObject

/** Optional setup UI. Drafts are durable; camera setup commits only at confirmation. */
class OnboardingActivity : Activity() {
    private enum class Page { PAIRED, WELCOME, STANDBY, CAMERA, TIMER, RIDE, SKIPPED, TRIGGERS, THRESHOLD, WIDGETS, FINAL }
    private lateinit var palette: DjiPalette
    private lateinit var content: LinearLayout
    private lateinit var footer: LinearLayout
    private var page = Page.PAIRED
    private val history = mutableListOf<Page>()
    private var standby = 300
    private var edit = 1
    private var inputDraft = "250"
    private var gradeDraft = "3"
    private var uphillGrade = DjiSettings.DEFAULT_UPHILL_GRADE_PERCENT
    private var downhillGrade = DjiSettings.DEFAULT_DESCENT_GRADE_PERCENT
    private var autoStart = false
    private var triggers = BooleanArray(8)
    private var values = intArrayOf(0, 250, 40, 0, 165, DjiSettings.DEFAULT_UPHILL_SPEED_KPH, DjiSettings.DEFAULT_DESCENT_SPEED_KPH, 0)
    private var requireSpeed = booleanArrayOf(true, true)
    private var speedRequiredDraft = true
    private var replay = false
    private val triggerNames = intArrayOf(R.string.highlight_lap, R.string.highlight_peak_power,
        R.string.highlight_max_speed, R.string.highlight_climb_summit, R.string.highlight_peak_heart_rate,
        R.string.highlight_uphill, R.string.highlight_high_speed_descent, R.string.highlight_ride_bookmarks)
    private val triggerDescriptions = intArrayOf(R.string.ob_lap, R.string.ob_power, R.string.ob_speed, R.string.ob_summit, R.string.ob_hr,
        R.string.highlight_uphill_summary, R.string.highlight_descent_summary, R.string.ob_bookmarks)
    private val triggerKeys = arrayOf(DjiSettings.KEY_LAP_HIGHLIGHTS, DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS,
        DjiSettings.KEY_MAX_SPEED_HIGHLIGHTS, DjiSettings.KEY_CLIMB_SUMMIT_HIGHLIGHTS, DjiSettings.KEY_PEAK_HEART_RATE_HIGHLIGHTS,
        DjiSettings.KEY_UPHILL_HIGHLIGHTS, DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, DjiSettings.KEY_RIDE_BOOKMARKS)
    private val valueKeys = mapOf(1 to DjiSettings.KEY_PEAK_POWER_WATTS, 2 to DjiSettings.KEY_MAX_SPEED_KPH, 4 to DjiSettings.KEY_PEAK_HEART_RATE_BPM,
        5 to DjiSettings.KEY_UPHILL_SPEED_KPH, 6 to DjiSettings.KEY_DESCENT_SPEED_KPH)

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        palette = DjiTheme.palette(this)
        restore()
        render()
    }

    private fun restore() {
        val prefs = DjiSettings.preferences(this)
        triggers[7] = prefs.getBoolean(DjiSettings.KEY_RIDE_BOOKMARKS, false)
        val raw = prefs.getString(DRAFT, null)
        if (raw != null) runCatching {
            val json = JSONObject(raw)
            page = Page.valueOf(json.getString("page"))
            standby = json.optInt("standby", 300).takeIf { it in listOf(60, 120, 300) } ?: 300
            edit = json.optInt("edit", 1).takeIf { it in valueKeys } ?: 1
            inputDraft = json.optString("input", "250")
            requireSpeed = booleanArrayOf(json.optBoolean("uphillRequireSpeed", true), json.optBoolean("descentRequireSpeed", true))
            speedRequiredDraft = json.optBoolean("speedRequiredDraft", true)
            replay = json.optBoolean("replay")
            autoStart = json.optBoolean("autoStart")
            uphillGrade = json.optInt("uphillGrade", DjiSettings.DEFAULT_UPHILL_GRADE_PERCENT)
            downhillGrade = json.optInt("downhillGrade", DjiSettings.DEFAULT_DESCENT_GRADE_PERCENT)
            gradeDraft = json.optString("gradeInput", if (edit == 6) downhillGrade.toString() else uphillGrade.toString())
            for (i in triggers.indices) {
                val savedTriggers = json.optJSONArray("triggers")
                if (savedTriggers != null && i < savedTriggers.length()) triggers[i] = savedTriggers.optBoolean(i)
                values[i] = json.optJSONArray("values")?.optInt(i, values[i]) ?: values[i]
            }
            val stack = json.optJSONArray("history")
            for (i in 0 until (stack?.length() ?: 0)) history.add(Page.valueOf(stack!!.getString(i)))
        }.onFailure { page = Page.WELCOME; history.clear() }
        if (raw == null && intent.getBooleanExtra("replay", false)) {
            replay = true
            page = Page.WELCOME
            val settings = DjiSettings.read(this)
            triggers = booleanArrayOf(settings.lapHighlights, settings.peakPowerHighlights, settings.maxSpeedHighlights,
                settings.climbSummitHighlights, settings.peakHeartRateHighlights,
                settings.uphillHighlights, settings.highSpeedDescentHighlights, settings.rideBookmarks)
            values = intArrayOf(0, settings.peakPowerWatts, settings.maxSpeedKph, 0, settings.peakHeartRateBpm,
                settings.uphillSpeedKph, settings.descentSpeedKph, 0)
            uphillGrade = settings.uphillGradePercent
            downhillGrade = settings.descentGradePercent
            requireSpeed = booleanArrayOf(settings.uphillRequireSpeed, settings.descentRequireSpeed)
            autoStart = settings.autoStartRecording
        }
        persist()
    }

    private fun persist() {
        val json = JSONObject().put("page", page.name).put("standby", standby).put("edit", edit)
            .put("gradeInput", gradeDraft).put("uphillGrade", uphillGrade).put("downhillGrade", downhillGrade)
            .put("uphillRequireSpeed", requireSpeed[0]).put("descentRequireSpeed", requireSpeed[1])
            .put("speedRequiredDraft", speedRequiredDraft)
            .put("input", inputDraft).put("replay", replay).put("autoStart", autoStart)
            .put("triggers", JSONArray(triggers.toList())).put("values", JSONArray(values.toList()))
            .put("history", JSONArray(history.map { it.name }))
        DjiSettings.preferences(this).edit().putString(DRAFT, json.toString()).apply()
    }
    private fun go(next: Page) { history.add(page); page = next; persist(); render() }
    @Deprecated("Uses Activity's platform back callback on supported Android versions")
    override fun onBackPressed() {
        if (history.isEmpty()) { complete(); return }
        val previous = history.removeAt(history.lastIndex)
        // Returning to camera editing suspends pulses through the existing API.
        if (previous == Page.CAMERA || previous == Page.STANDBY) DjiCameraController.setKeepAwake(this, false)
        page = previous; persist(); render()
    }
    private fun complete() {
        DjiSettings.preferences(this).edit().remove(DRAFT).putBoolean(DONE, true).apply()
        DjiWidgetController.refresh()
        setResult(RESULT_OK)
        finish()
    }
    private fun skipStandby() { DjiCameraController.setKeepAwake(this, false); go(Page.SKIPPED) }
    private fun saveTriggers() {
        val editor = DjiSettings.preferences(this).edit()
        triggerKeys.forEachIndexed { i, key -> editor.putBoolean(key, triggers[i]) }
        valueKeys.forEach { (i, key) -> editor.putInt(key, values[i]) }
        editor.putInt(DjiSettings.KEY_UPHILL_GRADE_PERCENT, uphillGrade)
            .putInt(DjiSettings.KEY_DESCENT_GRADE_PERCENT, downhillGrade)
        editor.putBoolean(DjiSettings.KEY_UPHILL_REQUIRE_SPEED, requireSpeed[0])
            .putBoolean(DjiSettings.KEY_DESCENT_REQUIRE_SPEED, requireSpeed[1])
        val any = triggers.any { it }
        editor.putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, any)
            .putBoolean(DjiSettings.KEY_AUTO_START_RECORDING, canAutoStart() && autoStart)
        editor.apply()
        DjiWidgetController.refresh()
    }
    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.appBackground)
            setPadding(dp(16), dp(4), dp(16), dp(10))
        }
        if (page !in listOf(Page.PAIRED, Page.WELCOME)) {
            root.addView(ImageButton(this).apply {
                setImageResource(R.drawable.ob_arrow_back); imageTintList = ColorStateList.valueOf(palette.textPrimary)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                contentDescription = getString(R.string.ob_back)
                setOnClickListener { onBackPressed() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isFillViewport = false; addView(content) }, LinearLayout.LayoutParams(-1, 0, 1f))
        footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(footer)
        setContentView(root)
        when (page) {
            Page.PAIRED -> {
                art(R.drawable.ic_widget_check, 108, true)
                title(R.string.pairing_connected); paragraph(R.string.ob_paired)
                action(R.string.ob_continue) { go(Page.WELCOME) }
            }
            Page.WELCOME -> {
                title(R.string.ob_welcome); paragraph(R.string.ob_intro)
                art(R.drawable.ob_devices, 145)
                action(R.string.ob_skip, false) { complete() }
                action(R.string.ob_start) { go(Page.STANDBY) }
            }
            Page.STANDBY -> {
                title(R.string.ob_prevent_title); paragraph(R.string.ob_prevent_body)
                paragraph(R.string.ob_prevent_recommend)
                art(R.drawable.ic_coffee, 72)
                paragraph(R.string.ob_battery, true)
                action(R.string.ob_skip, false) { skipStandby() }
                action(R.string.ob_enable) {
                    DjiCameraController.setKeepAwake(this, false)
                    go(Page.CAMERA)
                }
            }
            Page.CAMERA -> {
                title(R.string.ob_camera_title)
                heading(getString(R.string.ob_poweroff_title, standby / 60))
                paragraph(R.string.ob_poweroff_reason, true)
                heading(getString(R.string.ob_poweroff_path, standby / 60), 16f)
                content.addView(button(getString(R.string.ob_other_timer), false) { go(Page.TIMER) }.apply {
                    setTextColor(getColor(if (palette.textPrimary == android.graphics.Color.WHITE)
                        R.color.dji_surface_success else R.color.dji_confirm))
                })
                heading(getString(R.string.ob_mute)); paragraph(R.string.ob_mute_reason, true)
                heading(getString(R.string.ob_mute_path), 16f)
                heading(getString(R.string.ob_screen)); paragraph(R.string.ob_screen_reason, true)
                heading(getString(R.string.ob_screen_path), 16f)
                action(R.string.ob_skip, false) { skipStandby() }
                action(R.string.ob_next) {
                    // Interval is persisted before enabling; no scheduler or BLE changes.
                    DjiCameraController.setKeepAwakeInterval(this, OnboardingPolicy.intervalSeconds(standby))
                    DjiCameraController.setKeepAwake(this, true)
                    go(Page.RIDE)
                }
            }
            Page.TIMER -> {
                title(R.string.ob_timer_title); paragraph(R.string.ob_timer_body)
                listOf(60, 120, 300).forEach { seconds ->
                    content.addView(button(getString(R.string.ob_minutes, seconds / 60), seconds == standby) {
                        standby = seconds; persist(); onBackPressed()
                    })
                }
            }
            Page.RIDE -> {
                title(R.string.ob_ride_title)
                heading(getString(R.string.ob_turn_on)); paragraph(R.string.ob_auto_connect)
                heading(getString(R.string.ob_lock)); paragraph(R.string.ob_lock_body)
                art(R.drawable.dji_lock_menu, 130, tint = false); paragraph(R.string.ob_lock_tip, true)
                heading(getString(R.string.ob_prevents)); paragraph(R.string.ob_controls)
                action(R.string.ob_next) { go(Page.TRIGGERS) }
            }
            Page.SKIPPED -> {
                title(R.string.ob_later); art(R.drawable.ic_coffee, 72)
                paragraph(R.string.ob_later_body); paragraph(R.string.ob_later_faq)
                action(R.string.ob_next) { go(Page.TRIGGERS) }
            }
            Page.TRIGGERS -> {
                title(R.string.automatic_highlights); paragraph(R.string.ob_triggers_intro)
                triggerKeys.indices.forEach { i ->
                    val toggle = Switch(this).apply {
                        text = getString(triggerNames[i]); textSize = 18f; setTextColor(palette.textPrimary)
                        minHeight = dp(56); isChecked = triggers[i]
                        setOnCheckedChangeListener { _, checked ->
                            if (checked && i in valueKeys) {
                                openThreshold(i)
                            } else {
                                triggers[i] = checked
                                if (!canAutoStart()) autoStart = false
                                persist(); render()
                            }
                        }
                    }
                    content.addView(toggle); paragraph(triggerDescriptions[i], true)
                    if (triggers[i] && i in valueKeys) content.addView(button(thresholdLabel(i), false) {
                        openThreshold(i)
                    })
                }
                content.addView(Switch(this).apply {
                    text = getString(R.string.highlight_auto_start_recording); textSize = 18f
                    setTextColor(palette.textPrimary); setPadding(0, dp(24), 0, 0)
                    isEnabled = canAutoStart(); isChecked = autoStart && isEnabled; minHeight = dp(72)
                    setOnCheckedChangeListener { _, checked -> autoStart = checked; persist() }
                })
                paragraph(R.string.ob_auto_start_body, true)
                action(R.string.ob_skip, false) {
                    triggers.fill(false); autoStart = false; saveTriggers(); go(Page.WIDGETS)
                }
                action(R.string.ob_next) { saveTriggers(); go(Page.WIDGETS) }
            }
            Page.THRESHOLD -> {
                title(triggerNames[edit])
                val gradient = edit == 5 || edit == 6
                paragraph(if (gradient) R.string.ob_gradient_threshold else R.string.ob_threshold)
                var speedHeading: TextView? = null
                var speedToggle: Switch? = null
                val field = EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true)
                    setTextColor(palette.textPrimary); textSize = 24f
                    setText(inputDraft); contentDescription = if (gradient) getString(speedLabel(edit)) else "${getString(triggerNames[edit])} (${unit(edit)})"
                }
                val gradeField = if (gradient) EditText(this).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or (if (edit == 6) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
                    setSingleLine(true); setTextColor(palette.textPrimary); textSize = 24f
                    contentDescription = getString(gradeLabel(edit)); setText(gradeDraft)
                }.also { heading(getString(gradeLabel(edit)), 16f); content.addView(it) } else null
                if (gradient) {
                    speedToggle = Switch(this).apply {
                        text = getString(R.string.highlight_require_speed); textSize = 17f
                        setTextColor(palette.textPrimary); minHeight = dp(53)
                        isChecked = speedRequiredDraft
                    }
                    content.addView(speedToggle)
                    speedHeading = text(getString(speedLabel(edit)), 16f).apply {
                        visibility = if (speedRequiredDraft) View.VISIBLE else View.GONE
                    }
                    content.addView(speedHeading, params(8))
                    field.visibility = if (speedRequiredDraft) View.VISIBLE else View.GONE
                }
                content.addView(field)
                if (!gradient) heading(unit(edit), 16f)
                val note = text("", 16f, true); content.addView(note)
                fun speedValue() = if (gradient) OnboardingPolicy.gradientSpeed(field.text.toString(), edit == 5)
                    else OnboardingPolicy.threshold(field.text.toString())
                fun gradeValue() = gradeField?.let { OnboardingPolicy.gradient(it.text.toString(), edit == 5) }
                val save = action(R.string.ob_save_threshold) {
                    val speed = if (gradient && !speedRequiredDraft) values[edit] else speedValue()
                    val grade = gradeValue()
                    if (speed != null && (!gradient || grade != null)) {
                        values[edit] = speed
                        if (gradient) requireSpeed[edit - 5] = speedRequiredDraft
                        if (edit == 5) uphillGrade = grade!!
                        if (edit == 6) downhillGrade = grade!!
                        triggers[edit] = true; persist(); onBackPressed()
                    }
                }
                fun update() {
                    val value = speedValue()
                    save.isEnabled = (value != null || (gradient && !speedRequiredDraft)) && (!gradient || gradeValue() != null)
                    note.text = if (!save.isEnabled) getString(if (gradient) {
                        if (!speedRequiredDraft) {
                            if (edit == 5) R.string.ob_uphill_grade_range else R.string.ob_downhill_grade_range
                        } else if (edit == 5) R.string.ob_uphill_range else R.string.ob_downhill_range
                    } else R.string.ob_positive_number) else if (gradient) "" else feedback(edit, value!!)
                }
                fun watch(input: EditText, grade: Boolean) = input.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        if (grade) gradeDraft = s.toString() else inputDraft = s.toString()
                        persist(); update()
                    }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                speedToggle?.setOnCheckedChangeListener { _, checked ->
                    speedRequiredDraft = checked
                    field.visibility = if (checked) View.VISIBLE else View.GONE
                    speedHeading?.visibility = field.visibility
                    persist(); update()
                }
                watch(field, false); gradeField?.let { watch(it, true) }
                update()
            }
            Page.WIDGETS -> {
                title(R.string.ob_awesome); paragraph(R.string.ob_widgets); paragraph(R.string.ob_sizes)
                action(R.string.ob_next) { go(Page.FINAL) }
            }
            Page.FINAL -> {
                title(R.string.ob_fun); paragraph(R.string.ob_convenient)
                paragraph(R.string.ob_data); paragraph(R.string.ob_overlay)
                action(R.string.ob_done) { complete() }
            }
        }
    }
    private fun canAutoStart() = triggers.take(7).any { it }
    private fun openThreshold(i: Int) {
        edit = i; inputDraft = values[i].toString()
        speedRequiredDraft = if (i in 5..6) requireSpeed[i - 5] else true
        gradeDraft = (if (i == 6) downhillGrade else uphillGrade).toString()
        go(Page.THRESHOLD)
    }
    private fun speedLabel(i: Int) = if (i == 5) R.string.highlight_uphill_speed_threshold else R.string.highlight_descent_speed_threshold
    private fun gradeLabel(i: Int) = if (i == 5) R.string.highlight_uphill_grade_threshold else R.string.highlight_descent_grade_threshold
    private fun thresholdLabel(i: Int): String = when (i) {
        5 -> if (requireSpeed[0]) "${values[i]} km/h · +$uphillGrade%" else "+$uphillGrade%"
        6 -> if (requireSpeed[1]) "${values[i]} km/h · $downhillGrade%" else "$downhillGrade%"
        else -> "${values[i]} ${unit(i)}"
    }
    private fun unit(i: Int) = when (i) { 1 -> "W"; 2, 5, 6 -> "km/h"; else -> "bpm" }
    private fun feedback(i: Int, value: Int): String {
        val resources = when (i) {
            1 -> intArrayOf(0, R.string.ob_power_fun1, R.string.ob_power_fun2, R.string.ob_power_fun3)
            2 -> intArrayOf(0, R.string.ob_speed_fun1, R.string.ob_speed_fun2, R.string.ob_speed_fun3)
            else -> return ""
        }
        val level = if (i == 1) OnboardingPolicy.powerMessage(value) else OnboardingPolicy.speedMessage(value)
        return if (level == 0) "" else getString(resources[level])
    }
    private fun title(id: Int) = heading(getString(id), 19f)
    private fun heading(value: String, size: Float = 19f) {
        content.addView(text(value, size).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, params(14))
    }
    private fun paragraph(id: Int, muted: Boolean = false) { content.addView(text(getString(id), if (muted) 15f else 17f, muted), params(16)) }
    private fun text(value: String, size: Float, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (muted) palette.textSecondary else palette.textPrimary)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun art(id: Int, height: Int, success: Boolean = false, tint: Boolean = true) {
        content.addView(ImageView(this).apply {
            setImageResource(id); if (tint) imageTintList = ColorStateList.valueOf(if (success) getColor(R.color.dji_surface_success) else palette.textPrimary)
            adjustViewBounds = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, dp(height)).apply { bottomMargin = dp(14) })
    }
    private fun button(label: String, primary: Boolean, click: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 17f; minHeight = dp(48)
        setTextColor(if (primary) palette.appBackground else palette.textPrimary)
        backgroundTintList = ColorStateList.valueOf(if (primary) palette.textPrimary else palette.appBackground)
        setOnClickListener { click() }
    }
    private fun action(id: Int, primary: Boolean = true, click: () -> Unit): Button = button(getString(id), primary, click).also {
        it.textSize = 19f; it.minHeight = dp(53)
        footer.addView(it, LinearLayout.LayoutParams(-1, -2))
    }
    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        fun open(activity: Activity) {
            if (!DjiCameraController.state().configured) {
                DjiSettings.preferences(activity).edit().remove(DRAFT).apply()
                SetupActivity.open(activity)
                return
            }
            AlertDialog.Builder(activity)
                .setTitle(R.string.ob_restart_pairing_title)
                .setMessage(R.string.ob_restart_pairing_message)
                .setPositiveButton(R.string.ob_pair_again) { _, _ ->
                    DjiSettings.preferences(activity).edit().remove(DRAFT).apply()
                    DjiCameraController.forget(activity)
                    SetupActivity.open(activity)
                }
                .setNeutralButton(R.string.ob_skip_pairing) { _, _ ->
                    DjiSettings.preferences(activity).edit().remove(DRAFT).apply()
                    activity.startActivity(Intent(activity, OnboardingActivity::class.java).putExtra("replay", true))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        const val DRAFT = "onboarding_draft_v1"
        const val DONE = "onboarding_completed_v1"
        fun canResume(context: Context, connected: Boolean): Boolean {
            val raw = DjiSettings.preferences(context).getString(DRAFT, null) ?: return false
            val initial = runCatching { JSONObject(raw).getString("page") == Page.PAIRED.name }.getOrDefault(true)
            return connected || !initial
        }
        fun prepare(context: Context) {
            val prefs = DjiSettings.preferences(context)
            if (!prefs.getBoolean(DONE, false) && !prefs.contains(DRAFT)) {
                prefs.edit().putString(DRAFT, JSONObject().put("page", Page.PAIRED.name).toString())
                    .putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, false)
                    .putBoolean(DjiSettings.KEY_AUTO_START_RECORDING, false).apply()
            }
        }
    }
}
