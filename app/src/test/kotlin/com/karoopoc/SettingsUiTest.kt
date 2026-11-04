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
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.GradientSpeedTrigger
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
        if (name.endsWith("-bottom")) descendants(view).filterIsInstance<ScrollView>().filter { it.visibility == View.VISIBLE }.forEach {
            it.scrollTo(0, it.getChildAt(0).height)
        }
        if (name.contains("gradients")) {
            val scroll = activity.findViewById<ScrollView>(R.id.settings_scroll)
            val target = descendants(view).filterIsInstance<Switch>().first { it.text.toString() == activity.getString(if (name.contains("downhill")) R.string.highlight_high_speed_descent else R.string.highlight_uphill) }
            val rect = android.graphics.Rect()
            target.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(target, rect)
            scroll.scrollTo(0, rect.top - 12)
        }
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

    @Test fun rootTabsDoNotBecomeBackDestinationsAndContentExtendsBehindBackTab() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val root = render(activity,"qa2-navigation-overlay") as ViewGroup
        val content = root.getChildAt(0)
        assertEquals(root.height, content.height)
        val back = activity.findViewById<ImageButton>(R.id.karoo_back)
        assertEquals((54 * activity.resources.displayMetrics.density + .5f).toInt(), back.width)
        // The initial BLE permission prompt is unrelated to switching tabs.
        while (shadowOf(activity).nextStartedActivity != null) { }
        for (label in listOf(R.string.status_tab,R.string.help_tab,R.string.settings)) {
            views(activity).filterIsInstance<TextView>().first { it.text.toString() == activity.getString(label) }.performClick()
        }
        assertNull(shadowOf(activity).nextStartedActivity)
        back.performClick()
        assertTrue(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test fun `opening settings does not write defaults or change saved preferences`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = DjiSettings.preferences(context)
        prefs.edit().putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, true)
            .putBoolean(DjiSettings.KEY_HIDE_FIELD_CAPTIONS, true)
            .putInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, 37)
            .putInt(DjiSettings.KEY_PEAK_POWER_WATTS, 620)
            .putBoolean(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, true)
            .putInt(DjiSettings.KEY_DESCENT_SPEED_KPH, 65).commit()
        val before = prefs.all.toMap()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        render(controller.get(), "settings-saved-values")
        assertEquals(before, prefs.all)
        assertEquals(65, DjiSettings.read(controller.get()).descentSpeedKph)
        assertEquals(-3, DjiSettings.read(controller.get()).descentGradePercent)
        assertTrue(views(controller.get()).filterIsInstance<Switch>().first { it.text.toString() == "Keep camera awake" }.isChecked)
        controller.pause().stop().destroy()
    }

    @Test fun `white menus preserve defaults and controls under either system appearance`() {
        for (mode in listOf("dark", "light")) {
            RuntimeEnvironment.setQualifiers(if (mode == "dark") "+night" else "+notnight")
            val context = RuntimeEnvironment.getApplication()
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE, mode).commit()
            val before = DjiSettings.preferences(context).all.toMap()
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            val activity = controller.get()
            val switches = views(activity).filterIsInstance<Switch>().associate { it.text.toString() to it.isChecked }
            assertEquals(false, switches["Keep camera awake"])
            assertEquals(true, switches["Auto-highlights"])
            assertEquals(false, switches[activity.getString(R.string.highlight_ride_bookmarks)])
            assertEquals(true, switches["Low camera battery at 20%"])
            assertEquals(false, switches["Hide data field captions"])
            assertFalse(switches.containsKey("Photo and recording alerts"))
            val spinners = views(activity).filterIsInstance<Spinner>()
            assertEquals(0, spinners.size)
            button(activity, activity.getString(R.string.highlight_cooldown)).performClick()
            val cooldownDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            val editor = descendants(cooldownDialog.window!!.decorView).filterIsInstance<Spinner>().single()
            assertEquals("30 seconds", editor.selectedItem)
            assertTrue(descendants(cooldownDialog.window!!.decorView).filterIsInstance<TextView>().any {
                it.text.toString() == activity.getString(R.string.highlight_cooldown_summary)
            })
            cooldownDialog.dismiss()

            render(activity, "settings-$mode")
            activity.findViewById<ScrollView>(R.id.settings_scroll).fullScroll(View.FOCUS_DOWN)
            render(activity, "settings-$mode-bottom")
            activity.findViewById<ScrollView>(R.id.settings_scroll).scrollTo(0, 0)
            activity.onRequestPermissionsResult(1001, arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT), intArrayOf(0, 0))
            DjiCameraController.publish(DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED,
                batteryPercent = 84, remainingCapacityMb = 59_802, remainingRecordingSeconds = 8520,
                lastMessage = "Camera connected; ready"))
            button(activity, "Status").performClick()
            render(activity, "status-$mode")
            DjiCameraController.publish(DjiCameraState(lastMessage = "No camera paired"))
            render(activity, "status-$mode-unpaired")
            assertEquals(View.VISIBLE, button(activity, "Start Setup").visibility)
            button(activity, "Help").performClick()
            render(activity, "help-$mode")
            assertEquals(before, DjiSettings.preferences(context).all)
            controller.recreate()
            assertEquals(View.VISIBLE, controller.get().findViewById<ScrollView>(R.id.help_scroll).visibility)
            controller.pause().stop().destroy()
        }
    }

    @Test fun `connect remains disabled during the existing diagnostic pause`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        activity.onRequestPermissionsResult(1001,
            arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT), intArrayOf(0, 0))
        DjiCameraController.publish(DjiCameraState(configured = true, connection = CameraConnectionState.DISCONNECTED,
            diagnosticConnectionPaused = true))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(button(activity, activity.getString(R.string.connect_known_dji)).isEnabled)
        DjiCameraController.publish(DjiCameraState(configured = true, connection = CameraConnectionState.DISCONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(button(activity, activity.getString(R.string.connect_known_dji)).isEnabled)
        controller.pause().stop().destroy()
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
            assertNotNull(activity.findViewById<android.widget.ImageButton>(R.id.karoo_back))
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
        assertEquals(DjiSettings.DEFAULT_PEAK_POWER_WATTS, prefs.getInt(DjiSettings.KEY_PEAK_POWER_WATTS, 0))
        val master = views(activity).filterIsInstance<Switch>().first { it.text.toString() == "Auto-highlights" }
        master.isChecked = false
        assertTrue(prefs.getBoolean(DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS, false))
        master.isChecked = true
        assertTrue(power.isChecked)
        controller.pause().stop().destroy()
    }

    @Test fun `separate direction controls save values consumed by their triggers`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get()
        fun toggle(id: Int) = views(activity).filterIsInstance<Switch>().first { it.text.toString() == activity.getString(id) }
        fun input(id: Int) = views(activity).filterIsInstance<EditText>().first { field ->
            descendants(field.parent as View).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(id) }
        }
        fun save(id: Int, value: String) { input(id).setText(value); input(id).onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE) }
        assertFalse(toggle(R.string.highlight_uphill).isChecked)
        assertFalse(toggle(R.string.highlight_high_speed_descent).isChecked)
        assertEquals(50, DjiSettings.read(activity).descentSpeedKph)
        assertEquals(-3, DjiSettings.read(activity).descentGradePercent)
        toggle(R.string.highlight_uphill).isChecked = true
        toggle(R.string.highlight_high_speed_descent).isChecked = true
        save(R.string.highlight_uphill_speed_threshold, "18")
        save(R.string.highlight_uphill_grade_threshold, "+5")
        save(R.string.highlight_descent_speed_threshold, "65")
        save(R.string.highlight_descent_grade_threshold, "-8")
        val settings = DjiSettings.read(activity)
        assertEquals(18, settings.uphillSpeedKph)
        assertEquals(5, settings.uphillGradePercent)
        assertEquals(65, settings.descentSpeedKph)
        assertEquals(-8, settings.descentGradePercent)
        assertTrue(input(R.string.highlight_descent_grade_threshold).inputType and android.text.InputType.TYPE_NUMBER_FLAG_SIGNED != 0)
        assertTrue(GradientSpeedTrigger().update(18.0, 5.0, settings.uphillHighlights, settings.uphillSpeedKph, settings.uphillGradePercent))
        assertFalse(GradientSpeedTrigger().update(64.0, -8.0, settings.highSpeedDescentHighlights, settings.descentSpeedKph, settings.descentGradePercent))
        assertTrue(GradientSpeedTrigger().update(65.0, -8.0, settings.highSpeedDescentHighlights, settings.descentSpeedKph, settings.descentGradePercent))
        render(activity, "settings-gradients-light")
        render(activity, "settings-gradients-downhill-light")
        controller.recreate()
        activity = controller.get()
        assertEquals(-8, input(R.string.highlight_descent_grade_threshold).text.toString().toInt())
        toggle(R.string.highlight_uphill).isChecked = false
        assertTrue(toggle(R.string.highlight_high_speed_descent).isChecked)
        controller.pause().stop().destroy()
    }

    @Test @Config(qualifiers = "de-w256dp-h427dp-port-300dpi")
    fun `translated direction controls and help render with German resources`() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE, "dark")
            .putBoolean(DjiSettings.KEY_UPHILL_HIGHLIGHTS, true)
            .putBoolean(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, true).commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        render(controller.get(), "settings-gradients-de-dark")
        render(controller.get(), "settings-gradients-downhill-de-dark")
        val label = button(controller.get(), controller.get().getString(R.string.settings))
        assertTrue("German tab label must fit", label.paint.measureText(label.text.toString()) <= label.width - label.paddingLeft - label.paddingRight)
        assertTrue(views(controller.get()).filterIsInstance<Switch>().any { it.text.toString() == "Anstiegsmarker" })
        controller.pause().stop().destroy()
        val article = Robolectric.buildActivity(HelpActivity::class.java,
            Intent(context, HelpActivity::class.java).putExtra("help_file", "get-ready-for-a-ride.md")).setup()
        render(article.get(), "article-ride-de")
        article.pause().stop().destroy()
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
