package com.karoopoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import com.karoopoc.dji.AutomationMode
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiSettings
import com.karoopoc.widget.DjiWidgetController
import org.json.JSONArray
import org.json.JSONObject

/** Optional setup UI. Drafts are durable; camera setup commits only at confirmation. */
class OnboardingActivity : Activity() {
    private enum class Page { PAIRED, WELCOME, STANDBY, CAMERA, TIMER, RIDE, SKIPPED, AUTOMATION, TRIGGERS, CATALOG, THRESHOLD, WIDGETS, FINAL }
    private enum class AutomationChoice(val mode: AutomationMode?, val label: Int) {
        RECORDING(AutomationMode.RECORDING, R.string.ob_automation_recording),
        HIGHLIGHTS(AutomationMode.HIGHLIGHTS, R.string.ob_automation_highlights),
        PHOTOS(AutomationMode.PHOTOS, R.string.ob_automation_photos),
        OFF(null, R.string.ob_automation_off),
    }

    private lateinit var palette: DjiPalette
    private lateinit var ui: SettingsViews
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
    private var choice: AutomationChoice? = null
    private var automationSkipped = false
    private val catalog = AutomationTriggers.all
    private val triggers = BooleanArray(catalog.size)
    private val removed = BooleanArray(catalog.size)
    private val values = IntArray(catalog.size) { catalog[it].numbers.lastOrNull()?.default ?: 0 }
    private val requireSpeed = booleanArrayOf(true, true)
    private var speedRequiredDraft = true

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DjiTheme.activityContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        ui = SettingsViews(this)
        palette = ui.palette
        restore()
        render()
    }

    private fun restore() {
        val prefs = DjiSettings.preferences(this)
        standby = (prefs.getInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, 270) + 30).takeIf { it in listOf(60, 120, 300) } ?: 300
        catalog.forEachIndexed { index, trigger ->
            triggers[index] = prefs.getBoolean(trigger.key, false) && trigger.selected(this)
            trigger.numbers.lastOrNull()?.let { values[index] = prefs.getInt(it.key, it.default) }
        }
        uphillGrade = prefs.getInt(DjiSettings.KEY_UPHILL_GRADE_PERCENT, uphillGrade)
        downhillGrade = prefs.getInt(DjiSettings.KEY_DESCENT_GRADE_PERCENT, downhillGrade)
        requireSpeed[0] = prefs.getBoolean(DjiSettings.KEY_UPHILL_REQUIRE_SPEED, true)
        requireSpeed[1] = prefs.getBoolean(DjiSettings.KEY_DESCENT_REQUIRE_SPEED, true)

        val raw = prefs.getString(DRAFT, null)
        if (raw != null) runCatching {
            val json = JSONObject(raw)
            page = Page.valueOf(json.getString("page"))
            standby = json.optInt("standby", standby).takeIf { it in listOf(60, 120, 300) } ?: 300
            edit = json.optInt("edit", 1).takeIf { it in catalog.indices } ?: 1
            inputDraft = json.optString("input", values[edit].toString())
            requireSpeed[0] = json.optBoolean("uphillRequireSpeed", requireSpeed[0])
            requireSpeed[1] = json.optBoolean("descentRequireSpeed", requireSpeed[1])
            speedRequiredDraft = json.optBoolean("speedRequiredDraft", true)
            uphillGrade = json.optInt("uphillGrade", uphillGrade)
            downhillGrade = json.optInt("downhillGrade", downhillGrade)
            gradeDraft = json.optString("gradeInput", grade(edit).toString())
            automationSkipped = json.optBoolean("automationSkipped")
            val legacy = !json.has("automationChoice")
            choice = AutomationChoice.entries.firstOrNull { it.name == json.optString("automationChoice") }
            val savedTriggers = json.optJSONArray("triggers")
            for (i in triggers.indices) {
                if (savedTriggers != null && i < savedTriggers.length()) {
                    val wasEnabled = triggers[i]
                    triggers[i] = savedTriggers.optBoolean(i)
                    removed[i] = if (legacy) wasEnabled && !triggers[i] else json.optJSONArray("removed")?.optBoolean(i) == true
                }
                values[i] = json.optJSONArray("values")?.optInt(i, values[i]) ?: values[i]
            }
            val stack = json.optJSONArray("history")
            for (i in 0 until (stack?.length() ?: 0)) history.add(Page.valueOf(stack!!.getString(i)))
            if (legacy) {
                if (json.optBoolean("autoStart")) choice = AutomationChoice.RECORDING
                else if (triggers.any { it }) choice = AutomationChoice.HIGHLIGHTS
                when (page) {
                    Page.TRIGGERS -> page = Page.AUTOMATION
                    Page.THRESHOLD -> {
                        if (choice == null) choice = AutomationChoice.HIGHLIGHTS
                        if (history.lastOrNull() == Page.TRIGGERS) history.removeAt(history.lastIndex)
                        history.add(Page.AUTOMATION)
                        history.add(Page.TRIGGERS)
                        if (!triggers[edit]) history.add(Page.CATALOG)
                    }
                    else -> {
                        if (history.lastOrNull() == Page.TRIGGERS) history[history.lastIndex] = Page.AUTOMATION
                    }
                }
            }
            Unit
        }.onFailure { page = Page.WELCOME; history.clear() }
        if (raw == null && intent.getBooleanExtra("replay", false)) {
            page = if (intent.getBooleanExtra("paired", false)) Page.PAIRED else Page.WELCOME
            if (prefs.getBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, true) && triggers.any { it }) {
                choice = AutomationChoice.entries.first { it.mode == DjiSettings.mode(this) }
            }
        }
        persist()
    }

    private fun persist() {
        val json = JSONObject().put("page", page.name).put("standby", standby).put("edit", edit)
            .put("gradeInput", gradeDraft).put("uphillGrade", uphillGrade).put("downhillGrade", downhillGrade)
            .put("uphillRequireSpeed", requireSpeed[0]).put("descentRequireSpeed", requireSpeed[1])
            .put("speedRequiredDraft", speedRequiredDraft).put("input", inputDraft)
            .put("automationChoice", choice?.name.orEmpty()).put("automationSkipped", automationSkipped)
            .put("triggers", JSONArray(triggers.toList())).put("removed", JSONArray(removed.toList()))
            .put("values", JSONArray(values.toList())).put("history", JSONArray(history.map { it.name }))
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
    private fun finishAutomation(mode: AutomationMode?) {
        // Skipping turns automation off while retaining rules for later use in Settings.
        val editor = DjiSettings.preferences(this).edit()
            .putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, mode != null)
            .putBoolean(DjiSettings.KEY_AUTO_START_RECORDING, mode == AutomationMode.RECORDING)
        if (mode != null) {
            editor.putString(DjiSettings.KEY_AUTOMATION_MODE, mode.name)
            if (mode == AutomationMode.RECORDING) editor.putBoolean(DjiSettings.KEY_AUTO_STOP, true)
            availableTriggers().forEach { index ->
                val trigger = catalog[index]
                if (removed[index]) {
                    editor.putBoolean(trigger.selectionKey, false).putBoolean(trigger.key, false)
                } else if (triggers[index]) {
                    editor.putBoolean(trigger.selectionKey, true).putBoolean(trigger.key, true)
                    trigger.numbers.forEachIndexed { field, spec -> editor.putInt(spec.key, fieldValue(index, field)) }
                    trigger.speedKey?.let { editor.putBoolean(it, requireSpeed[index - 5]) }
                }
            }
        }
        editor.apply()
        automationSkipped = mode == null
        DjiCameraController.cancelAutomation(this)
        DjiWidgetController.refresh()
        go(Page.WIDGETS)
    }

    private fun render() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.appBackground)
            setPadding(0, dp(4), 0, 0)
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply {
            isFillViewport = false
            setPadding(dp(8), 0, dp(8), KarooNavigation.buttonSize(this@OnboardingActivity) + dp(20))
            clipToPadding = false
            addView(content)
        }, FrameLayout.LayoutParams(-1, -1))
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 0, 0, KarooNavigation.bottomInset(this@OnboardingActivity))
        }
        navigation.addView(KarooNavigation.backButton(this), LinearLayout.LayoutParams(KarooNavigation.buttonSize(this), KarooNavigation.buttonSize(this)))
        footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT }
        navigation.addView(footer, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(8)
        })
        root.addView(navigation, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        when (page) {
            Page.PAIRED -> {
                art(R.drawable.ic_widget_check, 108, true)
                title(R.string.pairing_connected); paragraph(R.string.ob_paired)
                action(R.string.ob_continue) { go(Page.WELCOME) }
            }
            Page.WELCOME -> {
                title(R.string.ob_welcome); paragraph(R.string.ob_intro)
                action(R.string.ob_skip, false) { complete() }
                action(R.string.ob_start) { go(Page.STANDBY) }
            }
            Page.STANDBY -> {
                title(R.string.ob_prevent_title); paragraph(R.string.ob_prevent_recommend)
                content.addView(text(getString(R.string.ob_battery), 15f, true).apply {
                    background = SettingsViews(this@OnboardingActivity).cardBackground()
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }, params(12))
                action(R.string.ob_skip, false) { skipStandby() }
                action(R.string.ob_enable) {
                    DjiCameraController.setKeepAwake(this, false)
                    go(Page.CAMERA)
                }
            }
            Page.CAMERA -> {
                title(R.string.ob_camera_title)
                cameraStep(getString(R.string.ob_poweroff_title, standby / 60), getString(R.string.ob_poweroff_path, standby / 60), R.string.ob_poweroff_reason)
                cameraStep(getString(R.string.ob_mute), getString(R.string.ob_mute_path), R.string.ob_mute_reason)
                cameraStep(getString(R.string.ob_screen), getString(R.string.ob_screen_path), R.string.ob_screen_reason)
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
                action(R.string.ob_next) { go(Page.AUTOMATION) }
            }
            Page.SKIPPED -> {
                title(R.string.ob_later); art(R.drawable.ic_coffee, 72)
                paragraph(R.string.ob_later_body); paragraph(R.string.ob_later_faq)
                action(R.string.ob_next) { go(Page.AUTOMATION) }
            }
            Page.AUTOMATION -> renderAutomationChoice()
            Page.TRIGGERS -> renderTriggers()
            Page.CATALOG -> renderCatalog()
            Page.THRESHOLD -> renderTriggerEditor()
            Page.WIDGETS, Page.FINAL -> {
                title(R.string.ob_awesome); paragraph(R.string.ob_widgets); paragraph(R.string.ob_sizes)
                if (page == Page.WIDGETS) {
                    if (automationSkipped) automationNote(R.string.ob_automation_skipped)
                    else choice?.takeIf { it.mode != null }?.let { automationNote(it.label) }
                }
                action(R.string.ob_done) { complete() }
            }
        }
    }

    private fun renderAutomationChoice() {
        automationStep(1)
        title(R.string.ob_automation_title)
        paragraph(R.string.ob_automation_example)
        val next = action(R.string.ob_next) {
            when (choice) {
                null -> Unit
                AutomationChoice.OFF -> finishAutomation(null)
                else -> go(Page.TRIGGERS)
            }
        }.apply { isEnabled = choice != null }
        val group = RadioGroup(this).apply { orientation = LinearLayout.VERTICAL }
        AutomationChoice.entries.forEach { option ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = getString(option.label)
                textSize = 16f
                minHeight = dp(48)
                setTextColor(palette.textPrimary)
                background = RippleDrawable(ColorStateList.valueOf(0x14000000), ui.cardBackground(), null)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
            group.addView(radio, RadioGroup.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            if (choice == option) group.check(radio.id)
            radio.setOnClickListener {
                choice = option
                next.isEnabled = true
                persist()
            }
        }
        content.addView(group, params(12))
    }

    private fun availableTriggers() = catalog.indices.filter { index ->
        choice != AutomationChoice.PHOTOS || !catalog[index].isRideBoundary
    }

    private fun renderTriggers() {
        automationStep(2)
        title(R.string.ob_automation_triggers)
        paragraph(R.string.ob_automation_trigger_intro)
        val available = availableTriggers()
        val active = available.filter { triggers[it] }
        active.forEach { index ->
            val trigger = catalog[index]
            val detail = trigger.thresholdSummary(
                value = { spec -> fieldValue(index, trigger.numbers.indexOf(spec)) },
                requireSpeed = trigger.speedKey == null || requireSpeed[index - 5],
            ).ifEmpty { getString(if (trigger.isRideBoundary) R.string.ob_automation_boundary else R.string.ob_automation_event) }
            content.addView(trigger.row(ui, detail) { openTrigger(index) }, params(8))
        }
        if (active.size < available.size) {
            content.addView(button(getString(R.string.automation_add_trigger), false) { go(Page.CATALOG) }, params(8))
        }
        val canContinue = active.isNotEmpty() &&
            (choice != AutomationChoice.RECORDING || active.any { !catalog[it].isRideBoundary })
        if (active.isNotEmpty() && !canContinue) paragraph(R.string.ob_automation_needs_start, true)
        automationNote(R.string.ob_automation_settings)
        action(R.string.ob_skip, false) { finishAutomation(null) }
        action(R.string.ob_next) { choice?.mode?.let(::finishAutomation) }.isEnabled = canContinue
    }

    private fun renderCatalog() {
        title(R.string.ob_automation_add_trigger)
        paragraph(R.string.automation_catalog_help)
        availableTriggers().filterNot { triggers[it] }.forEach { index ->
            val trigger = catalog[index]
            val description = if (trigger.isRideBoundary) R.string.automation_boundary_help else trigger.description
            content.addView(trigger.row(ui, getString(description)) { openTrigger(index) }, params(8))
        }
    }

    private fun openTrigger(index: Int) {
        edit = index
        inputDraft = values[index].toString()
        gradeDraft = grade(index).toString()
        speedRequiredDraft = catalog[index].speedKey == null || requireSpeed[index - 5]
        go(Page.THRESHOLD)
    }

    private fun renderTriggerEditor() {
        val trigger = catalog[edit]
        val existing = triggers[edit]
        val gradient = trigger.speedKey != null
        title(trigger.title)
        paragraph(if (trigger.isRideBoundary) R.string.automation_boundary_help else trigger.description)
        val speedToggle = if (gradient) Switch(this).apply {
            text = getString(R.string.highlight_require_speed)
            textSize = 16f
            minHeight = dp(48)
            isChecked = speedRequiredDraft
        } else null
        val fields = trigger.numbers.mapIndexed { index, spec ->
            if (index == 1 && speedToggle != null) content.addView(speedToggle, params())
            ui.numberInput(content, getString(spec.label), fieldValue(edit, index), spec.min < 0).apply {
                setText(if (gradient && index == 0) gradeDraft else inputDraft)
            }
        }
        val note = ui.secondaryText("")
        content.addView(note, params(8))
        if (existing) {
            content.addView(trigger.removeButton(ui) {
                triggers[edit] = false
                removed[edit] = true
                onBackPressed()
            }, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(16)
            })
        }
        fun fieldError(): TriggerNumber? = trigger.numbers.zip(fields).firstOrNull { (spec, field) ->
            val value = field.text.toString().toIntOrNull()
            field.visibility == View.VISIBLE && (value == null || value !in spec.min..spec.max)
        }?.first
        val save = action(if (existing) R.string.automation_save else R.string.automation_add) {
            if (fieldError() != null) return@action
            if (gradient) {
                val grade = fields.first().text.toString().toInt()
                if (edit == 5) uphillGrade = grade else downhillGrade = grade
                requireSpeed[edit - 5] = speedRequiredDraft
            }
            if (fields.isNotEmpty() && (!gradient || speedRequiredDraft)) {
                values[edit] = fields.last().text.toString().toInt()
            }
            triggers[edit] = true
            removed[edit] = false
            if (!existing && history.lastOrNull() == Page.CATALOG) history.removeAt(history.lastIndex)
            onBackPressed()
        }
        fun update() {
            val error = fieldError()
            save.isEnabled = error == null
            note.text = error?.let { getString(R.string.automation_invalid_number, it.min, it.max) }.orEmpty()
            note.visibility = if (error == null) View.GONE else View.VISIBLE
        }
        if (speedToggle != null) {
            val speed = fields.last()
            fun showSpeed() {
                val visibility = if (speedRequiredDraft) View.VISIBLE else View.GONE
                (speed.parent as View).visibility = visibility
                speed.visibility = visibility
            }
            showSpeed()
            speedToggle.setOnCheckedChangeListener { _, checked ->
                speedRequiredDraft = checked
                showSpeed()
                persist()
                update()
            }
        }
        fields.forEachIndexed { index, field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (gradient && index == 0) gradeDraft = s.toString() else inputDraft = s.toString()
                    persist()
                    update()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        update()
    }

    private fun grade(index: Int) = if (index == 6) downhillGrade else uphillGrade
    private fun fieldValue(index: Int, field: Int) =
        if (catalog[index].speedKey != null && field == 0) grade(index) else values[index]

    private fun automationStep(step: Int) {
        content.addView(ui.secondaryText(getString(R.string.ob_automation_step, step)), params(6))
    }

    private fun automationNote(id: Int) {
        content.addView(ui.secondaryText(getString(id)).apply {
            background = ui.cardBackground()
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }, params(12))
    }

    private fun title(id: Int) {
        content.addView(text(getString(id), 19f).apply { typeface = Typeface.DEFAULT_BOLD }, params(12))
    }
    private fun cameraStep(name: String, path: String, explanation: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(name, 17f).apply { typeface = Typeface.DEFAULT_BOLD }, params(4))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_dialog_info)
            scaleType = ImageView.ScaleType.FIT_CENTER
            imageTintList = ColorStateList.valueOf(getColor(R.color.karoo_forward_background))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            contentDescription = name
            setPadding(dp(15), dp(15), dp(15), dp(15))
            setOnClickListener { AlertDialog.Builder(this@OnboardingActivity)
                .setTitle(name).setMessage(explanation).setPositiveButton(android.R.string.ok, null).show() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        content.addView(row, params(4))
        content.addView(text(path, 15f), params(12))
    }
    private fun heading(value: String, size: Float = 17f) {
        content.addView(text(value, size).apply { typeface = Typeface.DEFAULT_BOLD }, params(4))
    }
    private fun paragraph(id: Int, muted: Boolean = false) { content.addView(text(getString(id), if (muted) 15f else 17f, muted), params(10)) }
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
    private fun action(id: Int, primary: Boolean = true, click: () -> Unit): Button {
        if (primary) {
            return KarooNavigation.forwardButton(this, getString(id), click).also {
                footer.addView(it, LinearLayout.LayoutParams(-2, KarooNavigation.buttonSize(this)))
            }
        }
        // Skip belongs to the page, after its instructions, rather than to the fixed navigation bar.
        return button(getString(id), false, click).also {
            it.textSize = 19f; it.minHeight = dp(53)
            content.addView(it, params(10))
        }
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
                val editor = prefs.edit().putString(DRAFT, JSONObject().put("page", Page.PAIRED.name).toString())
                if (!prefs.contains(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED)) {
                    editor.putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, false)
                }
                editor.apply()
            }
        }
    }
}
