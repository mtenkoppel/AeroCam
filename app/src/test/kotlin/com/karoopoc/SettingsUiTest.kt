package com.karoopoc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import org.json.JSONObject
import org.json.JSONArray
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
        fun node(v: View): JSONObject {
            val result = JSONObject().put("type", v.javaClass.simpleName).put("x", v.left).put("y", v.top)
                .put("width", v.width).put("height", v.height).put("visible", v.visibility == View.VISIBLE)
                .put("paddingLeft", v.paddingLeft).put("paddingTop", v.paddingTop)
                .put("paddingRight", v.paddingRight).put("paddingBottom", v.paddingBottom)
                .put("scrollX", v.scrollX).put("scrollY", v.scrollY)
            val bg = v.background
            if (bg is ColorDrawable) result.put("background", bg.color)
            if (bg is GradientDrawable) { result.put("background", bg.color?.defaultColor); result.put("radius", bg.cornerRadius) }
            if (v is TextView) {
                result.put("text", v.text.toString()).put("textSize", v.textSize).put("textColor", v.currentTextColor)
                    .put("bold", v.typeface?.isBold == true).put("gravity", v.gravity)
                    .put("lineHeight", v.lineHeight).put("compoundPaddingLeft", v.compoundPaddingLeft)
                    .put("compoundPaddingRight", v.compoundPaddingRight)
                val lines = JSONArray()
                v.layout?.let { l -> for (i in 0 until l.lineCount) lines.put(JSONObject()
                    .put("text", v.text.subSequence(l.getLineStart(i), l.getLineEnd(i)).toString())
                    .put("x", l.getLineLeft(i)).put("baseline", l.getLineBaseline(i))) }
                result.put("lines", lines)
            }
            if (v is Switch) result.put("checked", v.isChecked)
            if (v is ImageView && v.contentDescription != null) result.put("imageDescription", v.contentDescription.toString())
            if (v is ViewGroup) result.put("children", JSONArray((0 until v.childCount).map { node(v.getChildAt(it)) }))
            return result
        }
        File(folder, "$name.json").writeText(node(view).toString(2))
        return view
    }

    @Test fun `opening settings does not write defaults or change saved preferences`() {
        val context = RuntimeEnvironment.getApplication()
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
            val context = RuntimeEnvironment.getApplication()
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
            activity.findViewById<ScrollView>(R.id.settings_scroll).fullScroll(View.FOCUS_DOWN)
            render(activity, "settings-$mode-bottom")
            activity.findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 0)
            button(activity, "Status").performClick()
            render(activity, "status-$mode")
            button(activity, "Help").performClick()
            render(activity, "help-$mode")
            assertEquals(before, DjiSettings.preferences(context).all)
            controller.recreate()
            assertEquals(View.VISIBLE, controller.get().findViewById<ScrollView>(R.id.help_scroll).visibility)
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
        val context = RuntimeEnvironment.getApplication()
        assertEquals(5, HelpContent.articles.size)
        HelpContent.articles.forEach { article ->
            val intent = Intent(context, HelpActivity::class.java).putExtra("help_file", article.fileName)
            val controller = Robolectric.buildActivity(HelpActivity::class.java, intent).setup()
            val activity = controller.get()
            render(activity, "article-${article.fileName.removeSuffix(".md")}")
            assertTrue(views(activity).filterIsInstance<Toolbar>().isNotEmpty())
            assertFalse(views(activity).filterIsInstance<TextView>().any { it.text.toString() == "Settings" })
            activity.findViewById<ScrollView>(R.id.article_scroll).fullScroll(View.FOCUS_DOWN)
            render(activity, "article-${article.fileName.removeSuffix(".md")}-bottom")
            if (article.fileName == "get-ready-for-a-ride.md") assertEquals(1, views(activity).filterIsInstance<ImageView>().count { it.contentDescription == "Swipe down on the camera, then tap the highlighted lock." })
            controller.pause().stop().destroy()
        }
        val standby = HelpContent.load(context, "prevent-standby.md")
        assertFalse(standby.contains("QS"))
        assertFalse(standby.contains("## Battery use"))
        assertTrue(standby.contains("4 hours"))
    }

    @Test fun `numeric limits and disabled child preferences survive the UI refactor`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val power = views(activity).filterIsInstance<Switch>().first { it.text.toString() == "Peak power marker" }
        power.isChecked = true
        val input = views(activity).filterIsInstance<EditText>().first()
        input.setText("100")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        val prefs = DjiSettings.preferences(activity)
        assertEquals(300, prefs.getInt(DjiSettings.KEY_PEAK_POWER_WATTS, 500))
        input.setText("invalid")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        assertEquals(500, prefs.getInt(DjiSettings.KEY_PEAK_POWER_WATTS, 0))
        val master = views(activity).filterIsInstance<Switch>().first { it.text.toString() == "Auto-highlights" }
        master.isChecked = false
        assertTrue(prefs.getBoolean(DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS, false))
        master.isChecked = true
        assertTrue(power.isChecked)
        controller.pause().stop().destroy()
    }

    @Test fun `restarting onboarding preserves every saved setting`() {
        val context = RuntimeEnvironment.getApplication()
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
