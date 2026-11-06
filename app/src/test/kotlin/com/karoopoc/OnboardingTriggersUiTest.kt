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
import com.karoopoc.dji.AutomationMode
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
    private fun button(a: Activity, id: Int) =
        views(a).filterIsInstance<Button>().first { it.text.toString() == a.getString(id) }
    private fun click(a: Activity, id: Int) {
        val action = button(a, id)
        assertTrue(action.isEnabled)
        action.performClick()
    }
    private fun choose(a: Activity, id: Int) {
        views(a).filterIsInstance<RadioButton>().single { it.text.toString() == a.getString(id) }.performClick()
        click(a, R.string.ob_next)
    }
    private fun triggerRow(a: Activity, id: Int) =
        views(a).filterIsInstance<Button>().firstOrNull { it.text.toString().substringBefore('\n') == a.getString(id) }
    private fun openTrigger(a: Activity, id: Int) {
        click(a, R.string.automation_add_trigger)
        checkNotNull(triggerRow(a, id)).performClick()
    }
    private fun removeTrigger(a: Activity, id: Int) {
        checkNotNull(triggerRow(a, id)).performClick()
        click(a, R.string.automation_remove_trigger)
    }
    private fun toggle(a: Activity, id: Int) = views(a).filterIsInstance<Switch>().first { it.text.toString() == a.getString(id) }
    private fun input(a: Activity, id: Int) = views(a).filterIsInstance<EditText>().first { it.contentDescription == a.getString(id) }
    private fun save(a: Activity) = button(a, R.string.automation_add)
    private fun seed(json: JSONObject = JSONObject().put("page", "AUTOMATION")) {
        prefs.edit().putString(OnboardingActivity.DRAFT, json.toString()).commit()
    }
    private fun launch() = Robolectric.buildActivity(OnboardingActivity::class.java).setup()
    private fun render(a: Activity, name: String) {
        shadowOf(Looper.getMainLooper()).idle()
        val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(755, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 480, 755)
        val bitmap = Bitmap.createBitmap(480, 755, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val folder = File("build/reports/settings-ui/onboarding").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun bothDirectionsSaveSeparateThresholdsUsedByDetector() {
        seed(); val controller = launch(); val a = controller.get()
        choose(a, R.string.ob_automation_highlights)
        openTrigger(a, R.string.highlight_uphill)
        input(a, R.string.highlight_uphill_speed_threshold).setText("18")
        input(a, R.string.highlight_uphill_grade_threshold).setText("5")
        render(a, "uphill-thresholds-light"); save(a).performClick()
        openTrigger(a, R.string.highlight_high_speed_descent)
        input(a, R.string.highlight_descent_speed_threshold).setText("65")
        val grade = input(a, R.string.highlight_descent_grade_threshold)
        assertTrue(grade.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0)
        grade.setText("-8")
        render(a, "downhill-thresholds-light"); save(a).performClick()
        openTrigger(a, R.string.highlight_ride_bookmarks)
        click(a, R.string.automation_add)
        render(a, "gradient-bookmark-options-light")
        assertFalse(prefs.contains(DjiSettings.KEY_UPHILL_HIGHLIGHTS))
        assertFalse(prefs.contains("selected_${DjiSettings.KEY_UPHILL_HIGHLIGHTS}"))
        click(a, R.string.ob_next)
        val settings = DjiSettings.read(a)
        assertTrue(prefs.getBoolean("selected_${DjiSettings.KEY_UPHILL_HIGHLIGHTS}", false))
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
        choose(a, R.string.ob_automation_highlights)
        openTrigger(a, R.string.highlight_high_speed_descent)
        input(a, R.string.highlight_descent_speed_threshold).setText("70")
        input(a, R.string.highlight_descent_grade_threshold).setText("-")
        assertFalse(save(a).isEnabled)
        controller.pause().stop().destroy(); controller = launch(); a = controller.get()
        assertEquals("70", input(a, R.string.highlight_descent_speed_threshold).text.toString())
        assertEquals("-", input(a, R.string.highlight_descent_grade_threshold).text.toString())
        assertFalse(save(a).isEnabled)
        input(a, R.string.highlight_descent_grade_threshold).setText("5"); assertFalse(save(a).isEnabled)
        input(a, R.string.highlight_descent_grade_threshold).setText("-5"); assertTrue(save(a).isEnabled)
        a.onBackPressed() // Catalog
        a.onBackPressed() // Trigger list
        assertNull(triggerRow(a, R.string.highlight_high_speed_descent))
        assertFalse(button(a, R.string.ob_next).isEnabled)
        click(a, R.string.ob_skip)
        assertFalse(DjiSettings.read(a).highSpeedDescentHighlights)
        controller.pause().stop().destroy()
    }
    @Test fun oldFiveTriggerDraftRetainsValuesAndNewOptionsStartOff() {
        seed(JSONObject().put("page", "TRIGGERS").put("triggers", JSONArray(listOf(false,true,false,false,false)))
            .put("values", JSONArray(listOf(0,620,45,0,170))))
        val controller = launch(); val a = controller.get()
        click(a, R.string.ob_next) // Migrated draft preselects highlights.
        assertTrue(checkNotNull(triggerRow(a, R.string.highlight_peak_power)).text.contains("620 W"))
        listOf(R.string.highlight_uphill, R.string.highlight_high_speed_descent, R.string.highlight_ride_bookmarks)
            .forEach { assertNull(triggerRow(a, it)) }
        click(a,R.string.ob_next)
        val settings=DjiSettings.read(a)
        assertEquals(620,settings.peakPowerWatts)
        assertEquals(20,settings.uphillSpeedKph); assertEquals(50,settings.descentSpeedKph)
        controller.pause().stop().destroy()
    }
    @Test fun recordingRequiresAStartTriggerAndNoThanksPreservesConfiguredRules() {
        seed()
        val controller = launch(); val a = controller.get()
        assertFalse(button(a, R.string.ob_next).isEnabled)
        render(a, "automation-mode-light")
        choose(a, R.string.ob_automation_recording)
        render(a, "automation-triggers-empty-light")
        openTrigger(a, R.string.highlight_ride_bookmarks)
        click(a, R.string.automation_add)
        assertFalse(button(a, R.string.ob_next).isEnabled)
        openTrigger(a, R.string.highlight_lap)
        click(a, R.string.automation_add)
        click(a, R.string.ob_next)
        assertEquals(AutomationMode.RECORDING, DjiSettings.read(a).mode)
        assertTrue(DjiSettings.read(a).autoStop)
        assertTrue(DjiSettings.read(a).rideBookmarks)
        a.onBackPressed()
        a.onBackPressed()
        choose(a, R.string.ob_automation_off)
        assertTrue(DjiSettings.read(a).rideBookmarks)
        assertTrue(DjiSettings.read(a).lapHighlights)
        assertFalse(DjiSettings.read(a).autoHighlightsEnabled)
        assertTrue(views(a).filterIsInstance<TextView>().any { it.text.toString() == a.getString(R.string.ob_awesome) })
        controller.pause().stop().destroy()
    }

    @Test fun photosUseExplicitModeAndExcludeRideBoundariesFromTheCatalog() {
        prefs.edit().putString(DjiSettings.KEY_AUTOMATION_MODE, AutomationMode.RECORDING.name).commit()
        seed()
        val controller = launch(); val a = controller.get()
        choose(a, R.string.ob_automation_photos)
        click(a, R.string.automation_add_trigger)
        assertNull(triggerRow(a, R.string.highlight_ride_bookmarks))
        checkNotNull(triggerRow(a, R.string.highlight_lap)).performClick()
        click(a, R.string.automation_add)
        assertEquals(AutomationMode.RECORDING, DjiSettings.read(a).mode)
        click(a, R.string.ob_next)
        assertEquals(AutomationMode.PHOTOS, DjiSettings.read(a).mode)
        assertTrue(DjiSettings.read(a).lapHighlights)
        controller.pause().stop().destroy()
    }
    @Test @Config(qualifiers = "de-w256dp-h427dp-port-300dpi")
    fun translatedGradientEditorStaysLightWithLegacyDarkPreferenceAndSignedInputs() {
        prefs.edit().putString(DjiSettings.KEY_THEME_MODE,"dark").commit()
        seed(); val controller=launch(); val a=controller.get()
        render(a, "automation-mode-de")
        choose(a, R.string.ob_automation_highlights)
        openTrigger(a, R.string.highlight_high_speed_descent)
        assertEquals("-3", input(a, R.string.highlight_descent_grade_threshold).text.toString())
        render(a, "downhill-thresholds-de-dark")
        a.onBackPressed()
        checkNotNull(triggerRow(a, R.string.highlight_ride_bookmarks)).performClick()
        click(a, R.string.automation_add)
        render(a, "bookmark-option-de-dark")
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
        click(a, R.string.ob_next) // Replay preselects the saved mode.
        assertNotNull(triggerRow(a, R.string.highlight_uphill))
        assertNotNull(triggerRow(a, R.string.highlight_ride_bookmarks))
        assertEquals(before,DjiSettings.read(a))
        checkNotNull(triggerRow(a, R.string.highlight_high_speed_descent)).performClick()
        assertEquals("-8",input(a,R.string.highlight_descent_grade_threshold).text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun normalOnboardingKeepsExistingBookmarkChoiceUntilUserChangesIt() {
        prefs.edit().putBoolean(DjiSettings.KEY_RIDE_BOOKMARKS, true).commit()
        seed()
        val controller = launch(); val a = controller.get()
        choose(a, R.string.ob_automation_highlights)
        assertNotNull(triggerRow(a, R.string.highlight_ride_bookmarks))
        openTrigger(a, R.string.highlight_lap)
        click(a, R.string.automation_add)
        removeTrigger(a, R.string.highlight_ride_bookmarks)
        assertTrue(DjiSettings.read(a).rideBookmarks)
        click(a, R.string.ob_next)
        assertFalse(DjiSettings.read(a).rideBookmarks)
        assertFalse(prefs.getBoolean("selected_${DjiSettings.KEY_RIDE_BOOKMARKS}", true))
        controller.pause().stop().destroy()
    }
    @Test fun optionalSpeedSavesSeparatelyAndSurvivesDraftRestore() {
        seed(); var controller = launch(); var a = controller.get()
        choose(a, R.string.ob_automation_highlights)
        openTrigger(a, R.string.highlight_uphill)
        input(a, R.string.highlight_uphill_speed_threshold).setText("")
        input(a, R.string.highlight_uphill_grade_threshold).setText("10")
        assertFalse(save(a).isEnabled)
        toggle(a, R.string.highlight_require_speed).performClick()
        assertTrue(save(a).isEnabled)
        assertEquals(View.GONE, input(a, R.string.highlight_uphill_speed_threshold).visibility)
        render(a, "uphill-grade-only")
        controller.pause().stop().destroy(); controller = launch(); a = controller.get()
        assertFalse(toggle(a, R.string.highlight_require_speed).isChecked)
        assertTrue(save(a).isEnabled)
        save(a).performClick(); click(a, R.string.ob_next)
        val settings = DjiSettings.read(a)
        assertEquals(10, settings.uphillGradePercent)
        assertFalse(settings.uphillRequireSpeed)
        assertTrue(settings.descentRequireSpeed)
        controller.pause().stop().destroy()
    }
    @Test fun onboardingEntryRoutesUnpairedCameraToPairingAndKnownCameraThroughChoice() {
        val host = Robolectric.buildActivity(Activity::class.java).setup()
        val a = host.get()
        com.karoopoc.dji.DjiCameraController.publish(com.karoopoc.dji.DjiCameraState())
        seed(JSONObject().put("page", "CAMERA"))
        OnboardingActivity.open(a)
        assertEquals(SetupActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        assertFalse(prefs.contains(OnboardingActivity.DRAFT))
        com.karoopoc.dji.DjiCameraController.publish(com.karoopoc.dji.DjiCameraState(configured = true))
        OnboardingActivity.open(a)
        var dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        assertEquals(a.getString(R.string.ob_pair_again), dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text.toString())
        assertEquals(a.getString(R.string.ob_skip_pairing), dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).text.toString())
        assertEquals(a.getString(android.R.string.cancel), dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).text.toString())
        seed(JSONObject().put("page", "CAMERA"))
        val beforeCancel = prefs.all.toMap()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadowOf(a).nextStartedActivity)
        assertEquals(beforeCancel, prefs.all)
        assertTrue(com.karoopoc.dji.DjiCameraController.state().configured)
        OnboardingActivity.open(a)
        dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val skipPairing = shadowOf(a).nextStartedActivity
        assertEquals(OnboardingActivity::class.java.name, skipPairing.component!!.className)
        assertTrue(skipPairing.getBooleanExtra("replay", false))
        assertTrue(com.karoopoc.dji.DjiCameraController.state().configured)
        OnboardingActivity.open(a)
        dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(SetupActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        com.karoopoc.dji.DjiCameraController.publish(com.karoopoc.dji.DjiCameraState())
        host.pause().stop().destroy()
    }
    @Test fun sharedKarooBackSitsBesideActionsAndReturnsThroughOnboardingHistory() {
        for (page in listOf("CAMERA", "AUTOMATION", "TRIGGERS", "THRESHOLD", "WIDGETS")) {
            seed(JSONObject().put("page", page).put("automationChoice", "HIGHLIGHTS").put("history", JSONArray().put("WELCOME")))
            val controller = launch()
            val a = controller.get()
            render(a, "karoo-footer-$page")
            val back = a.findViewById<ImageButton>(R.id.karoo_back)
            assertEquals(1, views(a).count { it.id == R.id.karoo_back })
            val primary = views(a).filterIsInstance<Button>().last()
            val backPosition = IntArray(2); back.getLocationInWindow(backPosition)
            val actionPosition = IntArray(2); primary.getLocationInWindow(actionPosition)
            assertEquals(0, backPosition[0])
            assertEquals(backPosition[1] + back.height, actionPosition[1] + primary.height)
            assertTrue(actionPosition[0] >= backPosition[0] + back.width)
            assertEquals((54 * a.resources.displayMetrics.density + .5f).toInt(), back.width)
            assertEquals(back.width, back.height)
            assertEquals(back.height, primary.height)
            assertEquals(480, actionPosition[0] + primary.width)
            val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val rootPosition = IntArray(2); root.getLocationInWindow(rootPosition)
            assertEquals((10 * a.resources.displayMetrics.density + .5f).toInt(),
                rootPosition[1] + root.height - backPosition[1] - back.height)
            val scroll = views(a).filterIsInstance<ScrollView>().single()
            val skip = views(a).filterIsInstance<Button>().firstOrNull { it.text.toString() == a.getString(R.string.ob_skip) }
            if (skip != null) {
                assertTrue(descendants(scroll).contains(skip))
                assertEquals(skip, (skip.parent as ViewGroup).getChildAt((skip.parent as ViewGroup).childCount - 1))
            }
            assertEquals(primary.text.toString().uppercase(), primary.transformationMethod.getTransformation(primary.text, primary).toString())
            back.performClick()
            assertEquals("WELCOME", JSONObject(prefs.getString(OnboardingActivity.DRAFT, "")!!).getString("page"))
            assertTrue(views(a).filterIsInstance<TextView>().any { it.text.toString() == a.getString(R.string.ob_welcome) })
            controller.pause().stop().destroy()
        }
    }

    @Test fun compactCameraStepsUseDialogsAndAwesomeCompletesWithoutExtraPage() {
        seed(JSONObject().put("page", "CAMERA"))
        val camera = launch(); val a = camera.get()
        assertFalse(views(a).filterIsInstance<Button>().any { it.text.toString() == a.getString(R.string.ob_skip) || it.text.toString() == a.getString(R.string.ob_other_timer) })
        val info = views(a).filterIsInstance<ImageButton>().first { it.contentDescription == a.getString(R.string.ob_screen) }
        assertEquals(ImageView.ScaleType.FIT_CENTER, info.scaleType)
        info.performClick()
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(a.getString(R.string.ob_screen_reason), dialog.findViewById<TextView>(android.R.id.message).text.toString())
        dialog.dismiss()
        camera.pause().stop().destroy()
        for (page in listOf("WIDGETS", "FINAL")) {
            seed(JSONObject().put("page", page))
            val c = launch(); val activity = c.get()
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text.toString() == activity.getString(R.string.ob_awesome) })
            click(activity, R.string.ob_done)
            assertTrue(activity.isFinishing)
            assertTrue(prefs.getBoolean(OnboardingActivity.DONE, false))
            assertFalse(prefs.contains(OnboardingActivity.DRAFT))
            c.pause().stop().destroy()
        }
    }

    @Test fun renderPolishedOnboardingPagesInBothThemes() {
        for (theme in listOf("light", "dark")) {
            prefs.edit().putString(DjiSettings.KEY_THEME_MODE, theme).commit()
            for (page in listOf("WELCOME", "STANDBY", "CAMERA", "RIDE", "WIDGETS")) {
                seed(JSONObject().put("page", page))
                val controller = launch()
                render(controller.get(), "$page-$theme")
                controller.pause().stop().destroy()
            }
        }
    }
}
