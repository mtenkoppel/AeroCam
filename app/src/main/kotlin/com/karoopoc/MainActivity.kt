package com.karoopoc

import android.Manifest
import android.app.Activity
import android.content.Context
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.DjiSettings
import com.karoopoc.widget.DjiWidgetController

class MainActivity : Activity() {
    private enum class AppTab { SETTINGS, STATUS, HELP }
    private data class TabBinding(val label: TextView, val indicator: View, val content: View)
    private val tabs = linkedMapOf<AppTab, TabBinding>()
    private var activeTab = AppTab.SETTINGS
    private var cancelObservation: (() -> Unit)? = null
    private lateinit var ui: SettingsViews
    private lateinit var statusMessageValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var standbyValue: TextView
    private lateinit var storageValue: TextView
    private lateinit var connectionValue: TextView
    private lateinit var connectionHint: TextView
    private lateinit var startSetup: Button
    private lateinit var connectButton: Button
    private lateinit var forgetButton: Button
    private var hasBlePermission = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DjiTheme.activityContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DjiTheme.applyActivityTheme(this)
        window.requestFeature(Window.FEATURE_CONTENT_TRANSITIONS)
        super.onCreate(savedInstanceState)
        ui = SettingsViews(this)
        activeTab = savedInstanceState?.getString("tab")?.let { name -> AppTab.entries.find { it.name == name } } ?: AppTab.SETTINGS
        setContentView(createDashboard())
        selectTab(activeTab)
        if (requestBlePermissions()) observeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (::ui.isInitialized) {
            ui.refresh()
            render(DjiCameraController.state())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentFocus?.clearFocus()
        outState.putString("tab", activeTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cancelObservation?.invoke()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != BLE_PERMISSION_REQUEST) return
        hasBlePermission = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (hasBlePermission) observeCamera() else render(DjiCameraController.state())
    }

    private fun observeCamera() {
        hasBlePermission = true
        if (cancelObservation == null) cancelObservation = DjiCameraController.observe(this, ::render)
    }

    private fun createDashboard(): View {
        val root = ui.column().apply { setBackgroundColor(ui.palette.appBackground) }
        val tabBar = LinearLayout(this)
        AppTab.entries.forEach { tab ->
            val label = ui.text(getString(when (tab) {
                AppTab.SETTINGS -> R.string.settings
                AppTab.STATUS -> R.string.status_tab
                AppTab.HELP -> R.string.help_tab
            }), 16f, true).apply {
                gravity = Gravity.CENTER
                minHeight = ui.dp(48)
                setSingleLine(true)
                setHorizontallyScrolling(false)
                ellipsize = TextUtils.TruncateAt.END
                if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1, TypedValue.COMPLEX_UNIT_SP)
                isFocusable = true
                val value = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                setBackgroundResource(value.resourceId)
                setOnClickListener { currentFocus?.clearFocus(); selectTab(tab) }
            }
            val indicator = View(this)
            val tabContainer = ui.column().apply {
                addView(label, LinearLayout.LayoutParams(-1, ui.dp(48)))
                addView(indicator, LinearLayout.LayoutParams(-1, ui.dp(2)))
            }
            tabs[tab] = TabBinding(label, indicator, View(this))
            tabBar.addView(tabContainer, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabBar, ui.fullWidthParams())
        val content = FrameLayout(this)
        listOf(AppTab.SETTINGS to settingsTab(), AppTab.STATUS to statusTab(), AppTab.HELP to helpTab()).forEach { (tab, view) ->
            tabs[tab] = tabs.getValue(tab).copy(content = view)
            content.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun pageContent() = ui.column().apply { setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12)) }

    private fun settingsTab(): View {
        val content = pageContent()
        ui.standby(content)
        ui.automation(content)
        ui.alerts(content)
        ui.style(content)
        val advanced = ui.group(content, getString(R.string.advanced_settings))
        connectButton = ui.action(getString(R.string.connect_known_dji)) {
            if (requestBlePermissions()) { observeCamera(); DjiCameraController.connect(this) }
        }
        forgetButton = ui.action(getString(R.string.forget_dji_device)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.forget_dji_device)
                .setMessage(R.string.forget_confirmation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.forget_dji_device) { _, _ -> DjiCameraController.forget(this) }
                .show()
        }
        advanced.addView(connectButton, ui.fullWidthParams())
        advanced.addView(forgetButton, ui.fullWidthParams())
        advanced.addView(ui.action(getString(R.string.restart_onboarding)) { startActivity(android.content.Intent(this, OnboardingActivity::class.java).putExtra("replay", true)) }, ui.fullWidthParams())
        advanced.addView(ui.action(getString(R.string.reset_all_settings)) { showResetAllConfirmation() }, ui.fullWidthParams())
        return ui.scroll(content, R.id.settings_scroll)
    }

    private fun statusTab(): View {
        val content = pageContent()
        val information = ui.group(content, getString(R.string.group_information))
        statusMessageValue = infoRow(information, getString(R.string.debug_status), getString(R.string.starting), 3)
        batteryValue = infoRow(information, getString(R.string.battery), "–")
        standbyValue = infoRow(information, getString(R.string.prevent_standby), getString(R.string.off))
        storageValue = infoRow(information, getString(R.string.card_space), "–")
        val connection = ui.group(content, getString(R.string.connection))
        connection.addView(ui.text(getString(R.string.camera_name), 16f, true), ui.fullWidthParams())
        connectionValue = ui.text(getString(R.string.disconnected), 14f).apply { minLines = 2; maxLines = 2 }
        connection.addView(connectionValue, ui.fullWidthParams())
        connectionHint = ui.secondaryText(getString(R.string.no_camera_paired))
        connection.addView(connectionHint, ui.fullWidthParams())
        startSetup = Button(this).apply {
            text = getString(R.string.start_setup)
            isAllCaps = false
            setOnClickListener { SetupActivity.open(this@MainActivity) }
        }
        connection.addView(startSetup, ui.fullWidthParams())
        content.addView(ui.secondaryText(getString(R.string.dji_independence_notice)).apply { textSize = 11f }, ui.fullWidthParams(topMargin = ui.dp(8)))
        return ui.scroll(content, R.id.status_scroll)
    }

    private fun infoRow(parent: LinearLayout, label: String, initial: String, lines: Int = 2): TextView {
        val row = LinearLayout(this).apply {
            gravity = Gravity.TOP
            setPadding(0, ui.dp(12), 0, ui.dp(12))
        }
        row.addView(ui.secondaryText(label), LinearLayout.LayoutParams(0, -2, .42f))
        val value = ui.text(initial, 13f, true).apply {
            gravity = Gravity.END
            minLines = lines
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            setPadding(ui.dp(6), 0, 0, 0)
        }
        row.addView(value, LinearLayout.LayoutParams(0, -2, .58f))
        parent.addView(row, ui.fullWidthParams())
        return value
    }

    private fun helpTab(): View {
        val content = pageContent()
        HelpContent.articles.forEachIndexed { index, article ->
            val card = ui.column().apply {
                background = ui.cardBackground()
                setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            }
            val title = "${getString(R.string.help_number, index + 1)}  ${getString(article.titleRes)}"
            val label = SpannableStringBuilder(title + "\n" + getString(article.summaryRes)).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(AbsoluteSizeSpan(13, true), title.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(ui.palette.textSecondary), title.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            card.addView(ui.action(label) { HelpActivity.open(this, article.fileName) }, ui.fullWidthParams())
            content.addView(card, ui.fullWidthParams(bottomMargin = ui.dp(12)))
        }
        return ui.scroll(content, R.id.help_scroll)
    }

    private fun selectTab(tab: AppTab) {
        activeTab = tab
        tabs.forEach { (key, binding) ->
            val selected = key == tab
            binding.content.visibility = if (selected) View.VISIBLE else View.GONE
            binding.label.isSelected = selected
            binding.label.setTextColor(if (selected) ui.palette.textPrimary else ui.palette.textSecondary)
            binding.indicator.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            binding.indicator.setBackgroundColor(ui.palette.textPrimary)
        }
    }

    private fun render(state: DjiCameraState) {
        if (!::statusMessageValue.isInitialized) return
        statusMessageValue.text = if (hasBlePermission) state.lastMessage else getString(R.string.ble_permissions_denied)
        batteryValue.text = state.batteryPercent?.let { "$it%" } ?: "–"
        standbyValue.text = when {
            state.recording && state.keepAwakeEnabled -> getString(R.string.paused_while_recording)
            state.keepAwakeActive -> getString(R.string.active)
            state.keepAwakeEnabled -> getString(R.string.enabled)
            else -> getString(R.string.off)
        }
        storageValue.text = formatStorage(state.remainingCapacityMb, state.remainingRecordingSeconds)
        val connecting = state.connection == CameraConnectionState.CONNECTING || state.connection == CameraConnectionState.SEARCHING
        val connected = state.connection == CameraConnectionState.CONNECTED
        connectionValue.text = getString(when {
            !state.configured -> R.string.not_configured
            connected -> R.string.connected
            state.connection == CameraConnectionState.CONNECTING -> R.string.connecting
            state.connection == CameraConnectionState.SEARCHING -> R.string.searching
            else -> R.string.disconnected
        })
        // INVISIBLE keeps the connection card's footprint stable through reconnects.
        connectionHint.visibility = if (state.configured) View.INVISIBLE else View.VISIBLE
        startSetup.visibility = if (state.configured) View.INVISIBLE else View.VISIBLE
        connectButton.isEnabled = !connecting && !connected && state.configured && !state.diagnosticConnectionPaused
        forgetButton.isEnabled = hasBlePermission && state.configured
        ui.refresh()
    }

    private fun formatStorage(megabytes: Long?, remainingSeconds: Long?): String {
        if (megabytes == null && remainingSeconds == null) return "–"
        val capacity = when {
            megabytes == null -> "–"
            megabytes >= 1024 -> getString(R.string.storage_gb_format, megabytes / 1024.0)
            else -> getString(R.string.storage_mb_format, megabytes)
        }
        return remainingSeconds?.let { getString(R.string.storage_with_video_minutes, capacity, it / 60) } ?: capacity
    }

    private fun requestBlePermissions(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        hasBlePermission = missing.isEmpty()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), BLE_PERMISSION_REQUEST)
        return hasBlePermission
    }

    private fun showResetAllConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_all_settings)
            .setMessage(R.string.reset_all_settings_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.reset_all_settings) { _, _ ->
                DjiSettings.resetAll(this)
                DjiCameraController.forget(this)
                DjiWidgetController.refresh()
                recreate()
            }.show()
    }

    companion object { private const val BLE_PERMISSION_REQUEST = 1001 }
}
