package com.karoopoc.dji

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import com.karoopoc.SettingsViews
import com.karoopoc.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HighlightCooldownSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun reset() { DjiSettings.resetAll(context) }

    @Test fun defaultsPersistenceAndReset() {
        assertEquals(30, DjiSettings.read(context).highlightCooldownSeconds)
        for (value in listOf(0, 15, 30, 60)) {
            DjiSettings.setInt(context, DjiSettings.KEY_HIGHLIGHT_COOLDOWN_SECONDS, value)
            assertEquals(value, DjiSettings.read(context).highlightCooldownSeconds)
        }
        DjiSettings.setInt(context, DjiSettings.KEY_HIGHLIGHT_COOLDOWN_SECONDS, 999)
        assertEquals(30, DjiSettings.read(context).highlightCooldownSeconds)
        DjiSettings.resetAll(context)
        assertEquals(30, DjiSettings.read(context).highlightCooldownSeconds)
    }

    @Test fun selectorSavesEveryOptionAndRefreshesAfterReset() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val settings = SettingsViews(activity)
        val content = settings.column()
        settings.cooldown(content)
        activity.setContentView(content)
        fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        descendants(content).filterIsInstance<android.widget.Button>().single().performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        val spinner = descendants(dialog.window!!.decorView).filterIsInstance<Spinner>().single {
            it.contentDescription == activity.getString(R.string.highlight_cooldown)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, spinner.selectedItemPosition)
        for ((index, seconds) in HighlightCooldown.OPTIONS_SECONDS.withIndex()) {
            spinner.setSelection(index)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(seconds, DjiSettings.highlightCooldownSeconds(activity))
        }
        DjiSettings.resetAll(activity)
        settings.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, spinner.selectedItemPosition)
        assertEquals(30, DjiSettings.highlightCooldownSeconds(activity))
        dialog.dismiss()
    }

    @Test fun manualAndEventRequestsAreExemptByDefault() {
        DjiCameraController.addHighlight(context, "manual")
        val manual = shadowOf(context).nextStartedService
        assertFalse(manual.getBooleanExtra(DjiCameraService.EXTRA_HIGHLIGHT_COOLDOWN_LIMITED, false))
        DjiCameraController.addHighlight(context, "speed", cooldownLimited = true)
        val automatic = shadowOf(context).nextStartedService
        assertTrue(automatic.getBooleanExtra(DjiCameraService.EXTRA_HIGHLIGHT_COOLDOWN_LIMITED, false))
    }
}
