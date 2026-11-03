package com.karoopoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w256dp-h427dp-port-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsUiTest {
    @Before fun clearPreferences() { DjiSettings.preferences(RuntimeEnvironment.getApplication()).edit().clear().commit() }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun views(activity: Activity) = descendants(activity.findViewById(android.R.id.content))
    private fun button(activity: Activity, label: String) = views(activity).filterIsInstance<TextView>().first { it.text.toString() == label }
    private fun render(activity: Activity, name: String): View {
        shadowOf(Looper.getMainLooper()).idle()
        val view = (activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)
        view.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(755, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 480, 755)
        val bitmap = Bitmap.createBitmap(480, 755, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val folder = File("build/reports/settings-ui").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return view
    }

    @Test fun `opening settings does not write defaults or change saved preferences`() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val prefs = DjiSettings.preferences(context)
        prefs.edit().putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, true)
            .putBoolean(DjiSettings.KEY_HIDE_FIELD_CAPTIONS, true)
            .putInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, 37)
            .putInt(DjiSettings.KEY_PEAK_POWER_WATTS, 620).commit()
        val before = prefs.all.toMap()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        render(controller.get(), "settings-saved-values")
        assertEquals(before, prefs.all)
        assertTrue(views(controller.get()).filterIsInstance<Switch>().first { it.text.toString() == "Keep camera awake" }.isChecked)
        controller.pause().stop().destroy()
    }

    @Test fun `new UI retains original defaults and native controls in both appearances`() {
        for (mode in listOf("dark", "light")) {
            val context = RuntimeEnvironment.getApplication<android.app.Application>()
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE, mode).commit()
            val before = DjiSettings.preferences(context).all.toMap()
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            val activity = controller.get()
            val switches = views(activity).filterIsInstance<Switch>().associate { it.text.toString() to it.isChecked }
            assertEquals(false, switches["Keep camera awake"])
            assertEquals(true, switches["Auto-highlights"])
            assertEquals(true, switches["Ride start/end bookmarks"])
            assertEquals(true, switches["Low camera battery at 20%"])
            assertEquals(false, switches["Hide data field captions"])
            assertFalse(switches.containsKey("Photo and recording alerts"))
            assertEquals(1, views(activity).filterIsInstance<Spinner>().size)
            render(activity, "settings-$mode")
            button(activity, "Status").performClick()
            render(activity, "status-$mode")
            button(activity, "Help").performClick()
            render(activity, "help-$mode")
            assertEquals(before, DjiSettings.preferences(context).all)
            controller.pause().stop().destroy()
        }
    }

    @Test fun `debug and storage row heights stay fixed for changing content`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        button(activity, "Status").performClick()
        render(activity, "status-before")
        val status = views(activity).filterIsInstance<TextView>().first { it.minLines == 3 && it.maxLines == 3 }
        val battery = button(activity, "Battery")
        val location = IntArray(2)
        battery.getLocationOnScreen(location)
        val before = location[1]
        val height = status.height
        status.text = "A very long connection debug message that wraps over multiple lines while searching for the camera and waiting for its connection to become ready."
        render(activity, "status-long-debug")
        battery.getLocationOnScreen(location)
        assertEquals(height, status.height)
        assertEquals(before, location[1])
        controller.pause().stop().destroy()
    }

    @Test fun `reset uses platform confirmation and cancel preserves preferences`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val prefs = DjiSettings.preferences(activity)
        prefs.edit().putInt(DjiSettings.KEY_PEAK_POWER_WATTS, 650).commit()
        button(activity, "Reset all settings").performClick()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(650, prefs.getInt(DjiSettings.KEY_PEAK_POWER_WATTS, 500))
        controller.pause().stop().destroy()
    }

    @Test fun `all help pages render outside tab container with image and language fallback`() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        assertEquals(5, HelpContent.articles.size)
        HelpContent.articles.forEach { article ->
            val intent = Intent(context, HelpActivity::class.java).putExtra("help_file", article.fileName)
            val controller = Robolectric.buildActivity(HelpActivity::class.java, intent).setup()
            val activity = controller.get()
            render(activity, "article-${article.fileName.removeSuffix(".md")}")
            assertTrue(views(activity).filterIsInstance<Toolbar>().isNotEmpty())
            assertFalse(views(activity).filterIsInstance<TextView>().any { it.text.toString() == "Settings" })
            if (article.fileName == "get-ready-for-a-ride.md") assertEquals(1, views(activity).filterIsInstance<ImageView>().count { it.contentDescription == "Swipe down on the camera, then tap the highlighted lock." })
            controller.pause().stop().destroy()
        }
        val standby = HelpContent.load(context, "prevent-standby.md")
        assertFalse(standby.contains("QS"))
        assertFalse(standby.contains("## Battery use"))
        assertTrue(standby.contains("4 hours"))
    }

    @Test fun `restarting onboarding preserves every saved setting`() {
        val context = RuntimeEnvironment.getApplication<android.app.Application>()
        val prefs = DjiSettings.preferences(context)
        prefs.edit().putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, true)
            .putInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, 42).commit()
        val before = prefs.all.toMap()
        val controller = Robolectric.buildActivity(SetupActivity::class.java).setup()
        render(controller.get(), "setup-connect")
        assertEquals(before, prefs.all)
        controller.pause().stop().destroy()
    }
}
