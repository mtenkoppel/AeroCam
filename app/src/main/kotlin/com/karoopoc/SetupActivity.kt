package com.karoopoc

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.transition.Slide
import android.transition.TransitionManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraState

/** Replays setup without resetting pairing, defaults, or any saved setting. */
class SetupActivity : DetailActivity() {
    private enum class Page { CONNECT, CAMERA, RIDE, AUTOMATION, WIDGETS }
    private var page = Page.CONNECT
    private var cancelObservation: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var searchingUntil = 0L
    private lateinit var search: Button
    private lateinit var continueButton: Button
    private lateinit var summary: TextView
    private var permissionGranted = false
    private var pendingSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page")?.let { name -> Page.entries.find { it.name == name } } ?: Page.CONNECT
        searchingUntil = savedInstanceState?.getLong("searchingUntil") ?: 0L
        showPage()
        if (hasPermissions()) observeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) observeCamera()
        ui.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentFocus?.clearFocus()
        outState.putString("page", page.name)
        outState.putLong("searchingUntil", searchingUntil)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        cancelObservation?.invoke()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (page == Page.CONNECT) super.onBackPressed()
        else { currentFocus?.clearFocus(); page = Page.entries[page.ordinal - 1]; showPage(back = true) }
    }

    private fun next() {
        currentFocus?.clearFocus()
        page = Page.entries[page.ordinal + 1]
        showPage()
    }

    private fun button(label: Int, action: () -> Unit) = Button(this).apply {
        text = getString(label)
        isAllCaps = false
        textSize = 19f
        minHeight = ui.dp(53)
        setOnClickListener { action() }
    }

    private fun showPage(back: Boolean = false) {
        if (body.isLaidOut) TransitionManager.beginDelayedTransition(body, Slide(if (back) Gravity.START else Gravity.END))
        body.removeAllViews()
        // A fresh binding set prevents listeners retaining removed setup pages.
        ui = SettingsViews(this)
        findViewById<android.widget.ScrollView>(R.id.article_scroll)?.scrollTo(0, 0)
        when (page) {
            Page.CONNECT -> {
                body.addView(ui.text(getString(R.string.setup_connect_first), 19f, true), ui.fullWidthParams(bottomMargin = ui.dp(12)))
                body.addView(android.widget.ImageView(this).apply {
                    setImageResource(R.drawable.ob_devices)
                    imageTintList = android.content.res.ColorStateList.valueOf(ui.palette.textPrimary)
                    adjustViewBounds = true
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, android.widget.LinearLayout.LayoutParams(-1, ui.dp(145)))
                summary = ui.secondaryText(getString(R.string.pairing_subtitle)).apply { minLines = 3 }
                body.addView(summary, ui.fullWidthParams(bottomMargin = ui.dp(12)))
                listOf(R.string.pairing_step_1, R.string.pairing_step_2, R.string.pairing_step_3, R.string.pairing_step_4).forEach {
                    body.addView(ui.text(getString(it)), ui.fullWidthParams(bottomMargin = ui.dp(10)))
                }
                search = button(R.string.pairing_search) { requestSearch() }
                continueButton = button(R.string.continue_setup) {
                    startActivity(Intent(this, OnboardingActivity::class.java).putExtra("replay", true))
                    finish()
                }
                body.addView(search, ui.fullWidthParams())
                body.addView(continueButton, ui.fullWidthParams())
                body.addView(ui.secondaryText(getString(R.string.dji_independence_notice)).apply { textSize = 11f }, ui.fullWidthParams(topMargin = ui.dp(12)))
                renderConnection(DjiCameraController.state())
            }
            Page.CAMERA -> {
                body.addView(HelpContent.render(this, HelpContent.load(this, "recommended-camera-setup.md"), ui.palette, ui::dp))
                ui.standby(body)
                body.addView(button(R.string.continue_setup) { next() }, ui.fullWidthParams())
            }
            Page.RIDE -> {
                body.addView(HelpContent.render(this, HelpContent.load(this, "get-ready-for-a-ride.md"), ui.palette, ui::dp))
                body.addView(button(R.string.continue_setup) { next() }, ui.fullWidthParams())
            }
            Page.AUTOMATION -> {
                ui.automation(body)
                body.addView(button(R.string.continue_setup) { next() }, ui.fullWidthParams())
            }
            Page.WIDGETS -> {
                body.addView(ui.text(getString(R.string.setup_ready), 24f, true), ui.fullWidthParams(bottomMargin = ui.dp(12)))
                body.addView(ui.text(getString(R.string.setup_ready_summary)), ui.fullWidthParams(bottomMargin = ui.dp(16)))
                body.addView(ui.action(getString(R.string.help_widgets)) { HelpActivity.open(this, "different-widgets.md") }, ui.fullWidthParams())
                body.addView(button(R.string.finish_setup) { finishAfterTransition() }, ui.fullWidthParams())
            }
        }
    }

    private fun observeCamera() {
        permissionGranted = true
        if (cancelObservation == null) cancelObservation = DjiCameraController.observe(this, ::renderConnection)
    }

    private fun requestSearch() {
        if (!hasPermissions()) {
            pendingSearch = true
            requestPermissions(requiredPermissions(), PERMISSION_REQUEST)
            return
        }
        observeCamera()
        searchingUntil = SystemClock.elapsedRealtime() + 10_000L
        search.text = getString(R.string.pairing_searching)
        search.isEnabled = false
        if (DjiCameraController.state().configured) DjiCameraController.connect(this)
        else {
            OnboardingActivity.prepare(this)
            DjiCameraController.discover(this)
        }
        handler.postDelayed({ renderConnection(DjiCameraController.state()) }, 10_000L)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        permissionGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (permissionGranted) {
            observeCamera()
            if (pendingSearch) requestSearch()
        } else if (::summary.isInitialized) summary.text = getString(R.string.ble_permissions_denied)
        pendingSearch = false
    }

    private fun renderConnection(state: DjiCameraState) {
        if (page != Page.CONNECT || !::search.isInitialized) return
        val connecting = state.connection == CameraConnectionState.CONNECTING
        val searching = state.connection == CameraConnectionState.SEARCHING
        val latched = SystemClock.elapsedRealtime() < searchingUntil && !state.configured
        val connected = state.connection == CameraConnectionState.CONNECTED
        if (connecting || connected) searchingUntil = 0L
        search.text = getString(when {
            connecting -> R.string.pairing_accept
            searching && state.lastMessage.contains("found", ignoreCase = true) -> R.string.pairing_found
            searching || latched -> R.string.pairing_searching
            state.configured -> R.string.connect_known_dji
            else -> R.string.pairing_search
        })
        search.isEnabled = !connecting && !searching && !latched && !connected
        summary.text = getString(when {
            !permissionGranted -> R.string.ble_permissions_denied
            connected -> R.string.pairing_connected
            state.configured -> R.string.setup_already_paired
            searching && state.lastMessage.contains("found", ignoreCase = true) -> R.string.pairing_accept
            else -> R.string.pairing_subtitle
        })
        continueButton.visibility = if (state.configured) View.VISIBLE else View.INVISIBLE
        continueButton.isEnabled = state.configured && !connecting && !searching
    }

    private fun requiredPermissions() = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun hasPermissions(): Boolean = requiredPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }.also { permissionGranted = it }

    companion object {
        private const val PERMISSION_REQUEST = 1002
        fun open(activity: Activity) {
            activity.currentFocus?.clearFocus()
            activity.startActivity(Intent(activity, SetupActivity::class.java), ActivityOptions.makeSceneTransitionAnimation(activity).toBundle())
        }
    }
}
