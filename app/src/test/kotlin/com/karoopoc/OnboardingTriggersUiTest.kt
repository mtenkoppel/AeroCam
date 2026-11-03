package com.karoopoc

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.karoopoc.dji.DjiSettings
import com.karoopoc.dji.GradientSpeedTrigger
import org.json.JSONArray
import org.json.JSONObject
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w256dp-h427dp-port-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingTriggersUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = DjiSettings.preferences(context)
    @Before fun clearPreferences() { prefs.edit().clear().commit() }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun views(a: Activity) = descendants(a.findViewById(android.R.id.content))
    private fun click(a: Activity, id: Int) = views(a).filterIsInstance<Button>().first { it.text.toString() == a.getString(id) }.performClick()
    private fun toggle(a: Activity, id: Int) = views(a).filterIsInstance<Switch>().first { it.text.toString() == a.getString(id) }
    private fun input(a: Activity, id: Int) = views(a).filterIsInstance<EditText>().first { it.contentDescription == a.getString(id) }
    private fun save(a: Activity) = views(a).filterIsInstance<Button>().first { it.text.toString() == a.getString(R.string.ob_save_threshold) }
    private fun seed(json: JSONObject = JSONObject().put("page", "TRIGGERS")) {
        prefs.edit().putString(OnboardingActivity.DRAFT, json.toString()).commit()
    }
    private fun launch() = Robolectric.buildActivity(OnboardingActivity::class.java).setup()
    private fun render(a: Activity, name: String, scrollTo: Int? = null) {
        shadowOf(Looper.getMainLooper()).idle()
        val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(755, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 755)
        if (scrollTo != null) {
            val scroll = views(a).filterIsInstance<ScrollView>().single()
            val target = toggle(a, scrollTo)
            val rect = android.graphics.Rect(); target.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(target, rect); scroll.scrollTo(0, rect.top - 12)
        }
        val bitmap = Bitmap.createBitmap(480, 755, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val folder = File("build/reports/onboarding-ui").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun bothDirectionsSaveSeparateThresholdsUsedByDetector() {
        seed(); val controller = launch(); val a = controller.get()
        toggle(a, R.string.highlight_uphill).performClick()
        input(a, R.string.highlight_uphill_speed_threshold).setText("18")
        input(a, R.string.highlight_uphill_grade_threshold).setText("5")
        render(a, "uphill-thresholds-light"); save(a).performClick()
        toggle(a, R.string.highlight_high_speed_descent).performClick()
        input(a, R.string.highlight_descent_speed_threshold).setText("65")
        val grade = input(a, R.string.highlight_descent_grade_threshold)
        assertTrue(grade.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0)
        grade.setText("-8")
        render(a, "downhill-thresholds-light"); save(a).performClick()
        toggle(a, R.string.highlight_ride_bookmarks).performClick()
        render(a, "gradient-bookmark-options-light", R.string.highlight_uphill)
        assertFalse(prefs.contains(DjiSettings.KEY_UPHILL_HIGHLIGHTS))
        click(a, R.string.ob_next)
        val settings = DjiSettings.read(a)
        assertTrue(settings.uphillHighlights); assertTrue(settings.highSpeedDescentHighlights); assertTrue(settings.rideBookmarks)
        assertEquals(18, settings.uphillSpeedKph); assertEquals(5, settings.uphillGradePercent)
        assertEquals(65, settings.descentSpeedKph); assertEquals(-8, settings.descentGradePercent)
        val up = GradientSpeedTrigger(); val down = GradientSpeedTrigger()
        assertFalse(up.update(17.0, 6.0, settings.uphillHighlights, settings.uphillSpeedKph, settings.uphillGradePercent))
        assertTrue(up.update(18.0, 5.0, settings.uphillHighlights, settings.uphillSpeedKph, settings.uphillGradePercent))
        assertFalse(down.update(64.0, -9.0, settings.highSpeedDescentHighlights, settings.descentSpeedKph, settings.descentGradePercent))
        assertTrue(down.update(65.0, -8.0, settings.highSpeedDescentHighlights, settings.descentSpeedKph, settings.descentGradePercent))
        controller.pause().stop().destroy()
    }
    @Test fun incompleteSignedInputSurvivesRecreationAndCancelLeavesNewTriggerOff() {
        seed(); var controller = launch(); var a = controller.get()
        toggle(a, R.string.highlight_high_speed_descent).performClick()
        input(a, R.string.highlight_descent_speed_threshold).setText("70")
        input(a, R.string.highlight_descent_grade_threshold).setText("-")
        assertFalse(save(a).isEnabled)
        controller.pause().stop().destroy(); controller = launch(); a = controller.get()
        assertEquals("70", input(a, R.string.highlight_descent_speed_threshold).text.toString())
        assertEquals("-", input(a, R.string.highlight_descent_grade_threshold).text.toString())
        assertFalse(save(a).isEnabled)
        input(a, R.string.highlight_descent_grade_threshold).setText("5"); assertFalse(save(a).isEnabled)
        input(a, R.string.highlight_descent_grade_threshold).setText("-5"); assertTrue(save(a).isEnabled)
        a.onBackPressed(); assertFalse(toggle(a, R.string.highlight_high_speed_descent).isChecked)
        click(a, R.string.ob_next)
        assertFalse(DjiSettings.read(a).highSpeedDescentHighlights)
        controller.pause().stop().destroy()
    }
    @Test fun oldFiveTriggerDraftRetainsValuesAndNewOptionsStartOff() {
        seed(JSONObject().put("page", "TRIGGERS").put("triggers", JSONArray(listOf(false,true,false,false,false)))
            .put("values", JSONArray(listOf(0,620,45,0,170))))
        val controller = launch(); val a = controller.get()
        assertTrue(toggle(a, R.string.highlight_peak_power).isChecked)
        listOf(R.string.highlight_uphill,R.string.highlight_high_speed_descent,R.string.highlight_ride_bookmarks).forEach { assertFalse(toggle(a,it).isChecked) }
        click(a,R.string.ob_next)
        val settings=DjiSettings.read(a)
        assertEquals(620,settings.peakPowerWatts); assertEquals(45,settings.maxSpeedKph)
        assertEquals(20,settings.uphillSpeedKph); assertEquals(50,settings.descentSpeedKph)
        controller.pause().stop().destroy()
    }
    @Test fun bookmarkOnlySelectionCannotAutoStartAndSkipClearsEveryTrigger() {
        seed(); val controller=launch(); val a=controller.get()
        toggle(a,R.string.highlight_ride_bookmarks).performClick()
        assertFalse(toggle(a,R.string.highlight_auto_start_recording).isEnabled)
        click(a,R.string.ob_next)
        assertTrue(DjiSettings.read(a).rideBookmarks); assertTrue(DjiSettings.read(a).autoHighlightsEnabled)
        assertFalse(DjiSettings.read(a).autoStartRecording)
        a.onBackPressed(); click(a,R.string.ob_skip)
        assertFalse(DjiSettings.read(a).rideBookmarks); assertFalse(DjiSettings.read(a).autoHighlightsEnabled)
        assertFalse(DjiSettings.read(a).uphillHighlights); assertFalse(DjiSettings.read(a).highSpeedDescentHighlights)
        controller.pause().stop().destroy()
    }
    @Test fun replayLoadsExistingChoicesAndOpeningDoesNotOverwriteThem() {
        prefs.edit().putBoolean(DjiSettings.KEY_UPHILL_HIGHLIGHTS,true).putBoolean(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS,true)
            .putBoolean(DjiSettings.KEY_RIDE_BOOKMARKS,true).putInt(DjiSettings.KEY_UPHILL_SPEED_KPH,18)
            .putInt(DjiSettings.KEY_UPHILL_GRADE_PERCENT,5).putInt(DjiSettings.KEY_DESCENT_SPEED_KPH,65)
            .putInt(DjiSettings.KEY_DESCENT_GRADE_PERCENT,-8).commit()
        val before=DjiSettings.read(context)
        val controller=Robolectric.buildActivity(OnboardingActivity::class.java,Intent(context,OnboardingActivity::class.java).putExtra("replay",true)).setup()
        val a=controller.get(); click(a,R.string.ob_start); click(a,R.string.ob_skip); click(a,R.string.ob_next)
        assertTrue(toggle(a,R.string.highlight_uphill).isChecked); assertTrue(toggle(a,R.string.highlight_high_speed_descent).isChecked)
        assertTrue(toggle(a,R.string.highlight_ride_bookmarks).isChecked)
        assertEquals(before,DjiSettings.read(a))
        views(a).filterIsInstance<Button>().first { it.text.toString()=="65 km/h · -8%" }.performClick()
        assertEquals("-8",input(a,R.string.highlight_descent_grade_threshold).text.toString())
        controller.pause().stop().destroy()
    }
}
