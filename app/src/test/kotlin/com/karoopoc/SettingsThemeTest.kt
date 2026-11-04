package com.karoopoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.karoopoc.dji.DjiSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SettingsThemeTest {
    @Before fun clearPreferences() {
        DjiSettings.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit()
    }

    @Test fun `system follows a light system`() = checkControls(false, DjiThemeMode.SYSTEM)
    @Test fun `system follows a dark system`() = checkControls(true, DjiThemeMode.SYSTEM)
    @Test fun `light overrides a light system`() = checkControls(false, DjiThemeMode.LIGHT)
    @Test fun `light overrides a dark system`() = checkControls(true, DjiThemeMode.LIGHT)
    @Test fun `dark overrides a light system`() = checkControls(false, DjiThemeMode.DARK)
    @Test fun `dark overrides a dark system`() = checkControls(true, DjiThemeMode.DARK)

    private fun checkControls(systemDark: Boolean, mode: DjiThemeMode) {
        RuntimeEnvironment.setQualifiers(if (systemDark) "+night" else "+notnight")
        val application = RuntimeEnvironment.getApplication()
        DjiTheme.writeMode(application, mode)
        val before = DjiSettings.preferences(application).all.toMap()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val light = mode == DjiThemeMode.LIGHT || (mode == DjiThemeMode.SYSTEM && !systemDark)
            assertControls(activity, light)
            controller.recreate()
            assertControls(controller.get(), light)
            assertEquals("Rendering must preserve settings", before, DjiSettings.preferences(application).all)
            assertEquals("An app override must not change the system configuration", systemDark,
                application.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun assertControls(activity: Activity, light: Boolean) {
        assertEquals("Native resources must match the app palette", light,
            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES)
        assertEquals(light, DjiTheme.isLight(activity))
        val palette = SettingsViews(activity).palette
        assertEquals(if (light) Color.WHITE else Color.BLACK, palette.appBackground)
        val attributes = activity.obtainStyledAttributes(intArrayOf(android.R.attr.forceDarkAllowed))
        try { assertFalse("Android must not recolor an explicitly themed screen", attributes.getBoolean(0, true)) }
        finally { attributes.recycle() }

        val controls = descendants(activity.findViewById(android.R.id.content))
        val switches = controls.filterIsInstance<Switch>()
        assertTrue(switches.isNotEmpty())
        switches.forEach {
            assertReadable(it.currentTextColor, palette.previewBackground, "Switch ${it.text}")
            assertNativeColors(it.context, palette.previewBackground)
        }
        val inputs = controls.filterIsInstance<EditText>()
        assertTrue(inputs.isNotEmpty())
        inputs.forEach {
            assertReadable(it.currentTextColor, palette.previewBackground, "Numeric input")
            assertNativeColors(it.context, palette.previewBackground)
        }
        val spinners = controls.filterIsInstance<Spinner>()
        assertEquals(2, spinners.size)
        spinners.forEach { spinner ->
            for (index in 0 until spinner.adapter.count) {
                val selected = spinner.adapter.getView(index, null, spinner) as TextView
                val popup = spinner.adapter.getDropDownView(index, null, spinner) as TextView
                assertReadable(selected.currentTextColor, palette.previewBackground, "Spinner selection")
                assertReadable(popup.currentTextColor, nativeBackground(popup.context), "Spinner popup")
            }
        }
        controls.filterIsInstance<Button>().filter { it !is Switch && it.isEnabled }.forEach {
            assertReadable(it.currentTextColor, palette.previewBackground, "Button ${it.text}")
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Theme check").setMessage("Settings confirmation")
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).show()
        try {
            val background = nativeBackground(dialog.context)
            descendants(dialog.window!!.decorView).filterIsInstance<TextView>().filter { it.text.isNotEmpty() }.forEach {
                assertReadable(it.currentTextColor, background, "Dialog ${it.text}")
            }
        } finally { dialog.dismiss() }
    }

    private fun nativeBackground(context: Context): Int {
        val values = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        return try { values.getColor(0, Color.TRANSPARENT) } finally { values.recycle() }
    }

    private fun assertNativeColors(context: Context, background: Int) {
        val values = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorControlNormal))
        try {
            assertTrue("Native control outlines must contrast with their card", contrast(values.getColor(0, 0), background) >= 3)
        } finally { values.recycle() }
    }

    private fun assertReadable(foreground: Int, background: Int, label: String) {
        assertTrue("$label must have readable theme colors", contrast(foreground, background) >= 4.5)
    }

    private fun contrast(foreground: Int, background: Int): Double {
        fun luminance(color: Int): Double {
            fun linear(component: Int): Double {
                val value = component / 255.0
                return if (value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
            }
            return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color))
        }
        val alpha = Color.alpha(foreground) / 255.0
        fun blend(f: Int, b: Int) = (f * alpha + b * (1 - alpha)).toInt()
        val opaque = Color.rgb(blend(Color.red(foreground), Color.red(background)),
            blend(Color.green(foreground), Color.green(background)), blend(Color.blue(foreground), Color.blue(background)))
        val first = luminance(opaque)
        val second = luminance(background)
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
