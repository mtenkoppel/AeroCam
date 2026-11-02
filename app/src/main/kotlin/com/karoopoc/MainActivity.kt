package com.karoopoc

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.util.TypedValue
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.DjiOsmoBleClient
import com.karoopoc.dji.DjiSettings
import com.karoopoc.widget.DjiWidgetController

class MainActivity : Activity() {
    private enum class AppTab {
        SETTINGS,
        STATUS,
        HELP,
    }

    private data class TabViewBinding(
        val label: TextView,
        val indicator: View,
        val content: View,
    )

    private var cancelStateObservation: (() -> Unit)? = null
    private var renderingState = false
    private lateinit var palette: DjiPalette
    private val uiHandler = Handler(Looper.getMainLooper())
    private var pairingFlowActive = false
    private var pairingSuccessVisible = false
    private var pairingSearchingUntilMs = 0L

    private val tabs = linkedMapOf<AppTab, TabViewBinding>()
    private var activeTab = AppTab.SETTINGS
    private lateinit var dashboardRoot: View
    private lateinit var initialPairingRoot: View
    private lateinit var pairingSuccessRoot: View
    private lateinit var pairingButton: Button
    private lateinit var pairingSummary: TextView

    private lateinit var statusMessageValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var keepAwakeValue: TextView
    private lateinit var storageValue: TextView
    private lateinit var connectButton: Button
    private lateinit var forgetButton: Button
    private lateinit var resetButton: Button
    private lateinit var settingsConnectionValue: TextView
    private lateinit var settingsReconnectValue: TextView
    private lateinit var settingsConnectButton: Button

    private lateinit var peakPowerThresholdRow: LinearLayout
    private lateinit var maxSpeedThresholdRow: LinearLayout
    private lateinit var peakHeartRateThresholdRow: LinearLayout
    private lateinit var autoHighlightsContent: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        palette = DjiTheme.palette(this)
        Log.i(TAG, "AeroCam launched")
        setContentView(createDashboard())
        selectTab(AppTab.SETTINGS)

        if (requestBlePermissions()) {
            observeCamera()
        }
    }

    override fun onDestroy() {
        cancelStateObservation?.invoke()
        cancelStateObservation = null
        uiHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BLE_PERMISSION_REQUEST) return

        val granted = grantResults.isNotEmpty() && grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            observeCamera()
        } else {
            if (::statusMessageValue.isInitialized) {
                statusMessageValue.text = getString(R.string.ble_permissions_denied)
            }
            if (::pairingSummary.isInitialized) {
                pairingSummary.text = getString(R.string.ble_permissions_denied)
            }
        }
    }

    private fun observeCamera() {
        if (cancelStateObservation != null) return
        cancelStateObservation = DjiCameraController.observe(this, ::render)
    }

    private fun createDashboard(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.appBackground)
        }

        dashboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(palette.appBackground)
        }

        (dashboardRoot as LinearLayout).addView(createTabBar(), fullWidthParams(bottomMargin = dp(8)))

        val contentFrame = FrameLayout(this).apply {
            addView(createSettingsTab())
            addView(createStatusTab())
            addView(createHelpTab())
        }
        (dashboardRoot as LinearLayout).addView(contentFrame, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        initialPairingRoot = createInitialPairingView()
        pairingSuccessRoot = createPairingSuccessView()

        root.addView(
            dashboardRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            initialPairingRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            pairingSuccessRoot,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        return root
    }

    private fun createInitialPairingView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(28), dp(18), dp(18))
            setBackgroundColor(palette.appBackground)
        }

        content.addView(
            TextView(this).apply {
                text = getString(R.string.dashboard_title)
                textSize = 28f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(palette.textPrimary)
                gravity = Gravity.CENTER
            },
            fullWidthParams(bottomMargin = dp(10)),
        )

        pairingSummary = TextView(this).apply {
            text = getString(R.string.pairing_subtitle)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(palette.textSecondary)
        }
        content.addView(pairingSummary, fullWidthParams(bottomMargin = dp(22)))

        pairingButton = actionButton(getString(R.string.pairing_search)) {
            pairingFlowActive = true
            pairingSuccessVisible = false
            pairingSearchingUntilMs = SystemClock.elapsedRealtime() + 10_000L
            pairingButton.text = getString(R.string.pairing_searching)
            pairingButton.isEnabled = false
            pairingSummary.text = getString(R.string.pairing_subtitle)
            pairingButton.post { DjiCameraController.discover(this) }
        }
        content.addView(pairingButton, fullWidthParams(bottomMargin = dp(18)))

        val stepsGroup = group(content, getString(R.string.pairing_steps_title))
        stepsGroup.addView(stepText(getString(R.string.pairing_step_1)), fullWidthParams(bottomMargin = dp(8)))
        stepsGroup.addView(stepText(getString(R.string.pairing_step_2)), fullWidthParams(bottomMargin = dp(8)))
        stepsGroup.addView(stepText(getString(R.string.pairing_step_3)), fullWidthParams(bottomMargin = dp(8)))
        stepsGroup.addView(stepText(getString(R.string.pairing_step_4)), fullWidthParams())

        content.addView(independenceNotice(), fullWidthParams(topMargin = dp(18)))

        return scrollContainer(content)
    }

    private fun createPairingSuccessView(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(palette.appBackground)
            setOnClickListener { dismissPairingSuccess() }
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.pairing_connected_icon)
                    textSize = 44f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(getColor(R.color.dji_surface_success))
                    gravity = Gravity.CENTER
                },
                fullWidthParams(bottomMargin = dp(12)),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = getString(R.string.pairing_connected)
                    textSize = 24f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(palette.textPrimary)
                    gravity = Gravity.CENTER
                },
                fullWidthParams(),
            )
        }
    }

    private fun createTabBar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createTabButton(AppTab.SETTINGS, getString(R.string.settings)))
            addView(createTabButton(AppTab.STATUS, getString(R.string.status_tab)))
            addView(createTabButton(AppTab.HELP, getString(R.string.help_tab)))
        }
    }

    private fun createTabButton(tab: AppTab, title: String): View {
        val label = TextView(this).apply {
            text = title
            gravity = Gravity.CENTER
            textSize = 18f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(2), dp(5), dp(2), dp(4))
            setSingleLine(true)
            setAutoSizeTextTypeUniformWithConfiguration(12, 18, 1, TypedValue.COMPLEX_UNIT_SP)
            setOnClickListener { selectTab(tab) }
        }
        val indicator = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3),
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(indicator)
        }
        tabs[tab] = TabViewBinding(label = label, indicator = indicator, content = View(this))
        return container.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun createSettingsTab(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val connectionGroup = group(content, getString(R.string.connection))
        settingsConnectionValue = infoRow(
            connectionGroup,
            getString(R.string.camera_status),
            getString(R.string.disconnected),
        )
        settingsReconnectValue = secondaryText(
            "${getString(R.string.gallery_state_reconnecting)}…",
        )
        connectionGroup.addView(settingsReconnectValue, fullWidthParams(bottomMargin = dp(10)))
        settingsConnectButton = actionButton(getString(R.string.connect_known_dji)) {
            DjiCameraController.connect(this)
        }
        connectionGroup.addView(settingsConnectButton, fullWidthParams())

        val automationGroup = group(content, getString(R.string.automatic_highlights))
        val autoHighlightsSwitch = settingsSwitch(
            automationGroup,
            getString(R.string.automatic_highlights_enabled),
            DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED,
            true,
        ) { checked ->
            autoHighlightsContent.visibility = if (checked) View.VISIBLE else View.GONE
        }
        autoHighlightsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (autoHighlightsSwitch.isChecked) View.VISIBLE else View.GONE
        }
        automationGroup.addView(
            autoHighlightsContent,
            fullWidthParams(),
        )

        autoHighlightsContent.addView(
            secondaryText(getString(R.string.automatic_highlights_summary)),
            fullWidthParams(bottomMargin = dp(12)),
        )
        settingsSwitch(
            autoHighlightsContent,
            getString(R.string.highlight_auto_start_recording),
            DjiSettings.KEY_AUTO_START_RECORDING,
            false,
        )
        autoHighlightsContent.addView(
            secondaryText(getString(R.string.highlight_auto_start_recording_summary)),
            fullWidthParams(bottomMargin = dp(12)),
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
        settingsSwitch(
            triggerGroup,
            getString(R.string.highlight_ride_bookmarks),
            DjiSettings.KEY_RIDE_BOOKMARKS,
            true,
        )

        val alertsGroup = group(content, getString(R.string.alert_settings))
        settingsSwitch(
            alertsGroup,
            getString(R.string.alert_low_battery_setting),
            DjiSettings.KEY_LOW_BATTERY_ALERT,
            true,
        )
        settingsSwitch(
            alertsGroup,
            getString(R.string.alert_low_storage_setting),
            DjiSettings.KEY_LOW_STORAGE_ALERT,
            false,
        )
        settingsSwitch(
            alertsGroup,
            getString(R.string.alert_action_notifications_setting),
            DjiSettings.KEY_ACTION_NOTIFICATIONS,
            false,
        )

        val styleGroup = group(content, getString(R.string.theme_mode))
        settingsSwitch(
            styleGroup,
            getString(R.string.hide_field_captions),
            DjiSettings.KEY_HIDE_FIELD_CAPTIONS,
            false,
        ) {
            DjiWidgetController.refresh()
        }
        styleGroup.addView(themeModeRow(), fullWidthParams())

        return scrollContainer(content).also { tabs[AppTab.SETTINGS] = tabs.getValue(AppTab.SETTINGS).copy(content = it) }
    }

    private fun createStatusTab(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val infoGroup = group(content, getString(R.string.group_information))
        statusMessageValue = infoRow(infoGroup, getString(R.string.debug_status), getString(R.string.starting))
        statusMessageValue.apply {
            minLines = 3
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        batteryValue = infoRow(infoGroup, getString(R.string.battery), "-")
        keepAwakeValue = infoRow(infoGroup, getString(R.string.keep_osmo_awake), getString(R.string.off))
        storageValue = infoRow(infoGroup, getString(R.string.card_space), "-")

        val actionsGroup = group(content, getString(R.string.group_actions))
        connectButton = actionButton(getString(R.string.connect_known_dji)) {
            DjiCameraController.connect(this)
        }
        forgetButton = actionButton(getString(R.string.forget_dji_device)) {
            DjiCameraController.forget(this)
        }
        resetButton = actionButton(getString(R.string.reset_all_settings)) {
            showResetAllConfirmation()
        }
        actionsGroup.addView(connectButton, fullWidthParams(bottomMargin = dp(14)))
        actionsGroup.addView(forgetButton, fullWidthParams(bottomMargin = dp(14)))
        actionsGroup.addView(resetButton, fullWidthParams())

        content.addView(independenceNotice(), fullWidthParams(topMargin = dp(18), bottomMargin = dp(8)))

        return scrollContainer(content).also { tabs[AppTab.STATUS] = tabs.getValue(AppTab.STATUS).copy(content = it) }
    }

    private fun independenceNotice() = TextView(this).apply {
        text = getString(R.string.dji_independence_notice)
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(palette.textSecondary)
    }

    private fun createHelpTab(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        showHelpIndex(content)
        return scrollContainer(content).also { tabs[AppTab.HELP] = tabs.getValue(AppTab.HELP).copy(content = it) }
    }

    private fun showHelpIndex(content: LinearLayout) {
        content.removeAllViews()
        content.addView(
            TextView(this).apply {
                text = getString(R.string.help_intro)
                textSize = 15f
                setTextColor(palette.textSecondary)
            },
            fullWidthParams(bottomMargin = dp(10)),
        )
        HelpContent.articles.forEach { article ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = roundedBackground(palette.previewBackground)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(article.titleRes)
                        textSize = 17f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(palette.textPrimary)
                    },
                    fullWidthParams(bottomMargin = dp(4)),
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = getString(article.summaryRes)
                        textSize = 13f
                        setTextColor(palette.textSecondary)
                    },
                    fullWidthParams(),
                )
                setOnClickListener { showHelpArticle(content, article) }
            }
            content.addView(row, fullWidthParams(bottomMargin = dp(10)))
        }
    }

    private fun showHelpArticle(content: LinearLayout, article: HelpArticle) {
        content.removeAllViews()
        content.addView(
            actionButton(getString(R.string.help_back)) { showHelpIndex(content) },
            fullWidthParams(bottomMargin = dp(10)),
        )
        content.addView(
            HelpContent.render(
                context = this,
                markdown = HelpContent.load(this, article.fileName),
                palette = palette,
                dp = ::dp,
            ),
            fullWidthParams(bottomMargin = dp(16)),
        )
    }

    private fun selectTab(tab: AppTab) {
        activeTab = tab
        tabs.forEach { (key, binding) ->
            val selected = key == tab
            binding.content.visibility = if (selected) View.VISIBLE else View.GONE
            binding.label.setTextColor(if (selected) palette.textPrimary else palette.textSecondary)
            binding.indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            binding.indicator.setBackgroundColor(palette.textPrimary)
        }
    }

    private fun render(state: DjiCameraState) {
        renderingState = true
        updatePairingSurface(state)
        statusMessageValue.text = state.lastMessage
        batteryValue.text = state.batteryPercent?.let { "$it%" } ?: "-"
        keepAwakeValue.text = when {
            state.recording && state.keepAwakeEnabled -> getString(R.string.paused_while_recording)
            state.keepAwakeActive -> getString(R.string.active)
            state.keepAwakeEnabled -> getString(R.string.enabled)
            else -> getString(R.string.off)
        }
        storageValue.text = formatStorage(state.remainingCapacityMb, state.remainingRecordingSeconds)

        val connecting = state.connection == CameraConnectionState.CONNECTING ||
            state.connection == CameraConnectionState.SEARCHING
        val connected = state.connection == CameraConnectionState.CONNECTED
        settingsConnectionValue.text = getString(
            if (connected) R.string.connected else R.string.disconnected,
        )
        settingsReconnectValue.visibility = if (connecting) View.VISIBLE else View.GONE
        settingsConnectButton.visibility = if (connected) View.GONE else View.VISIBLE
        settingsConnectButton.isEnabled = state.configured && !connecting && !state.diagnosticConnectionPaused
        connectButton.isEnabled = state.configured && !connecting && !connected
        forgetButton.isEnabled = state.configured
        renderingState = false
    }

    private fun updatePairingSurface(state: DjiCameraState) {
        if (pairingFlowActive && state.configured && state.connection == CameraConnectionState.CONNECTED) {
            showPairingSuccess()
        }

        if (pairingSuccessVisible) {
            dashboardRoot.visibility = View.GONE
            initialPairingRoot.visibility = View.GONE
            pairingSuccessRoot.visibility = View.VISIBLE
            return
        }

        val needsInitialPairing = !state.configured
        initialPairingRoot.visibility = if (needsInitialPairing) View.VISIBLE else View.GONE
        dashboardRoot.visibility = if (needsInitialPairing) View.GONE else View.VISIBLE
        pairingSuccessRoot.visibility = View.GONE

        if (!::pairingButton.isInitialized || !::pairingSummary.isInitialized) return
        val searchLatchActive = SystemClock.elapsedRealtime() < pairingSearchingUntilMs

        when {
            state.connection == CameraConnectionState.CONNECTING -> {
                pairingSearchingUntilMs = 0L
                pairingButton.text = getString(R.string.pairing_accept)
                pairingButton.isEnabled = false
                pairingSummary.text = getString(R.string.pairing_subtitle)
            }

            state.connection == CameraConnectionState.SEARCHING -> {
                val found = state.lastMessage.contains("found", ignoreCase = true)
                pairingButton.text = if (found) {
                    getString(R.string.pairing_found)
                } else {
                    getString(R.string.pairing_searching)
                }
                pairingButton.isEnabled = false
                pairingSummary.text = if (found) {
                    getString(R.string.pairing_accept)
                } else {
                    getString(R.string.pairing_subtitle)
                }
            }

            searchLatchActive && needsInitialPairing -> {
                pairingButton.text = getString(R.string.pairing_searching)
                pairingButton.isEnabled = false
                pairingSummary.text = getString(R.string.pairing_subtitle)
            }

            needsInitialPairing -> {
                pairingSearchingUntilMs = 0L
                pairingButton.text = getString(R.string.pairing_search)
                pairingButton.isEnabled = true
                pairingSummary.text = getString(R.string.pairing_subtitle)
            }

            else -> {
                pairingFlowActive = false
                pairingSearchingUntilMs = 0L
                pairingButton.text = getString(R.string.pairing_search)
                pairingButton.isEnabled = true
                pairingSummary.text = state.lastMessage
            }
        }
    }

    private fun showPairingSuccess() {
        if (pairingSuccessVisible) return
        pairingFlowActive = false
        pairingSuccessVisible = true
        pairingSearchingUntilMs = 0L
        uiHandler.removeCallbacksAndMessages(null)
        uiHandler.postDelayed({ dismissPairingSuccess() }, 2_000L)
    }

    private fun dismissPairingSuccess() {
        pairingSuccessVisible = false
        dashboardRoot.visibility = View.VISIBLE
        initialPairingRoot.visibility = View.GONE
        pairingSuccessRoot.visibility = View.GONE
        selectTab(AppTab.SETTINGS)
    }

    private fun settingsSwitch(
        parent: LinearLayout,
        label: String,
        key: String,
        default: Boolean,
        onChanged: ((Boolean) -> Unit)? = null,
    ): Switch {
        val row = Switch(this).apply {
            text = label
            textSize = 16f
            setTextColor(palette.textPrimary)
            isChecked = DjiSettings.preferences(this@MainActivity).getBoolean(key, default)
            applySwitchColors()
            setOnCheckedChangeListener { _, checked ->
                if (renderingState) return@setOnCheckedChangeListener
                DjiSettings.setBoolean(this@MainActivity, key, checked)
                onChanged?.invoke(checked)
            }
        }
        parent.addView(row, fullWidthParams(bottomMargin = dp(8)))
        return row
    }

    private fun themeModeRow(): LinearLayout {
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    getString(R.string.theme_system),
                    getString(R.string.theme_dark),
                    getString(R.string.theme_light),
                ),
            )
            setSelection(
                when (DjiTheme.readMode(this@MainActivity)) {
                    DjiThemeMode.SYSTEM -> 0
                    DjiThemeMode.DARK -> 1
                    DjiThemeMode.LIGHT -> 2
                },
                false,
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val next = when (position) {
                        1 -> DjiThemeMode.DARK
                        2 -> DjiThemeMode.LIGHT
                        else -> DjiThemeMode.SYSTEM
                    }
                    if (next == DjiTheme.readMode(this@MainActivity)) return
                    DjiTheme.writeMode(this@MainActivity, next)
                    DjiWidgetController.refresh()
                    recreate()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                labelText(getString(R.string.theme_mode)),
                fullWidthParams(bottomMargin = dp(6)),
            )
            addView(spinner, fullWidthParams())
        }
    }

    private fun numberRow(
        label: String,
        key: String,
        default: Int,
        minimum: Int,
        maximum: Int,
    ): LinearLayout {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setTextColor(palette.textPrimary)
            setText(DjiSettings.preferences(this@MainActivity).getInt(key, default).toString())
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

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, 0, 0)
            addView(
                labelText(label),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(input, LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT))
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
        DjiSettings.setInt(this, key, value)
    }

    private fun group(parent: LinearLayout, title: String): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(palette.previewBackground)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 18f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(palette.textPrimary)
                },
                fullWidthParams(bottomMargin = dp(10)),
            )
        }
        parent.addView(container, fullWidthParams(bottomMargin = dp(10)))
        return container
    }

    private fun subgroup(parent: LinearLayout, title: String): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@MainActivity).apply {
                    text = title
                    textSize = 16f
                    setTextColor(palette.textPrimary)
                },
                fullWidthParams(bottomMargin = dp(8)),
            )
            addView(
                View(this@MainActivity).apply {
                    setBackgroundColor(palette.textSecondary and 0x66FFFFFF)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1),
                ).apply {
                    bottomMargin = dp(10)
                },
            )
        }
        parent.addView(container, fullWidthParams(bottomMargin = dp(2)))
        return container
    }

    private fun infoRow(parent: LinearLayout, label: String, value: String): TextView {
        val valueView = TextView(this).apply {
            text = value
            textSize = 17f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(palette.textPrimary)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@MainActivity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(palette.textSecondary)
                },
                fullWidthParams(bottomMargin = dp(2)),
            )
            addView(valueView, fullWidthParams())
        }
        parent.addView(row, fullWidthParams(bottomMargin = dp(16)))
        return valueView
    }

    private fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }
    }

    private fun labelText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(palette.textPrimary)
        }
    }

    private fun secondaryText(text: String): TextView {
        return TextView(this).apply {
            textSize = 13f
            setTextColor(palette.textSecondary)
            this.text = text
        }
    }

    private fun stepText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(palette.textSecondary)
        }
    }

    private fun scrollContainer(content: LinearLayout): ScrollView {
        return ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(palette.appBackground)
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun roundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(color)
        }
    }

    private fun Switch.applySwitchColors() {
        val onColor = getColor(R.color.dji_surface_success)
        val offThumb = if (DjiTheme.readMode(this@MainActivity) == DjiThemeMode.LIGHT) {
            getColor(R.color.dji_bg_preview_light)
        } else {
            getColor(R.color.dji_text_primary_dark)
        }
        val offTrack = getColor(R.color.dji_neutral)
        thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(onColor, offThumb),
        )
        trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(onColor, offTrack),
        )
    }

    private fun fullWidthParams(
        topMargin: Int = 0,
        bottomMargin: Int = 0,
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatStorage(megabytes: Long?, remainingSeconds: Long?): String {
        if (megabytes == null && remainingSeconds == null) return "-"
        val capacity = if (megabytes == null) {
            "-"
        } else if (megabytes >= 1_024) {
            getString(R.string.storage_gb_format, megabytes / 1_024.0)
        } else {
            getString(R.string.storage_mb_format, megabytes)
        }
        return remainingSeconds?.let { getString(R.string.storage_with_video_minutes, capacity, it / 60) } ?: capacity
    }

    private fun requestBlePermissions(): Boolean {
        val missing = requiredBlePermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return true
        requestPermissions(missing.toTypedArray(), BLE_PERMISSION_REQUEST)
        return false
    }

    private fun requiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun showResetAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_all_settings))
            .setMessage(getString(R.string.reset_all_settings_confirmation))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.reset_all_settings)) { _, _ ->
                DjiSettings.resetAll(this)
                DjiCameraController.forget(this)
                DjiWidgetController.refresh()
                recreate()
            }
            .show()
    }

    companion object {
        private const val TAG = "KarooPoC"
        private const val BLE_PERMISSION_REQUEST = 1001
    }
}
