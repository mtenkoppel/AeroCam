package com.karoopoc

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraState
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "w256dp-h427dp-port-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PairingScreensUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        DjiSettings.preferences(context).edit().clear().commit()
        DjiCameraController.publish(DjiCameraState())
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    private fun views(a: Activity) = descendants(a.findViewById(android.R.id.content))
    private fun button(a: Activity, id: Int) = views(a).filterIsInstance<Button>().first { it.text.toString() == a.getString(id) }
    private fun save(a: Activity, name: String) {
        val root = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(755, View.MeasureSpec.EXACTLY))
        root.layout(0,0,480,755)
        val bitmap=Bitmap.createBitmap(480,755,Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val folder=File("build/reports/settings-ui/pairing").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun pairingStartHasBrandSearchBeforeInstructionsAndBackInBothThemes() {
        for (theme in listOf("light","dark")) {
            DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE,theme).commit()
            val controller=Robolectric.buildActivity(SetupActivity::class.java).setup()
            val a=controller.get()
            val text=views(a).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(text.contains(a.getString(R.string.app_name)))
            assertTrue(text.contains(a.getString(R.string.pairing_subtitle)))
            assertTrue(text.indexOf(a.getString(R.string.pairing_search)) < text.indexOf(a.getString(R.string.pairing_connect_camera)))
            assertTrue(text.contains(a.getString(R.string.pairing_step_4)))
            assertFalse(text.contains(a.getString(R.string.ble_permissions_denied)))
            assertNotNull(a.findViewById<android.widget.ImageButton>(R.id.karoo_back))
            save(a,"pairing-start-$theme")
            a.findViewById<android.widget.ImageButton>(R.id.karoo_back).performClick();assertTrue(a.isFinishing)
            controller.pause().stop().destroy()
        }
    }
    @Test fun existingConnectedSnapshotDoesNotSkipRePairStartingScreen() {
        DjiCameraController.publish(DjiCameraState(configured=true,connection=CameraConnectionState.CONNECTED))
        val controller=Robolectric.buildActivity(SetupActivity::class.java).setup()
        val a=controller.get()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(a.isFinishing)
        assertNull(shadowOf(a).nextStartedActivity)
        assertTrue(views(a).filterIsInstance<TextView>().any { it.text.toString()==a.getString(R.string.pairing_connect_camera) })
        controller.pause().stop().destroy()
        DjiCameraController.publish(DjiCameraState())
    }
    @Test fun searchedConnectionShowsPairedConfirmationBeforeWelcomeAndPreservesChoices() {
        shadowOf(context).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)
        DjiSettings.preferences(context).edit().putString(DjiSettings.KEY_THEME_MODE,"dark").putBoolean(OnboardingActivity.DONE,true)
            .putBoolean(DjiSettings.KEY_PEAK_POWER_HIGHLIGHTS,true).putInt(DjiSettings.KEY_PEAK_POWER_WATTS,420).commit()
        val before=DjiSettings.read(context)
        val controller=Robolectric.buildActivity(SetupActivity::class.java).setup()
        val a=controller.get();button(a,R.string.pairing_search).performClick()
        DjiCameraController.publish(DjiCameraState(configured=true,connection=CameraConnectionState.CONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        val next=shadowOf(a).nextStartedActivity
        assertEquals(OnboardingActivity::class.java.name,next.component!!.className)
        assertTrue(next.getBooleanExtra("paired",false))
        assertTrue(a.isFinishing)
        val onboarding=Robolectric.buildActivity(OnboardingActivity::class.java,next).setup()
        val page=onboarding.get()
        assertTrue(views(page).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.pairing_connected) })
        assertTrue(views(page).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.ob_paired) })
        assertEquals(before,DjiSettings.read(page))
        save(page,"pairing-connected")
        button(page,R.string.ob_continue).performClick()
        assertTrue(views(page).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.ob_welcome) })
        assertEquals(before,DjiSettings.read(page))
        onboarding.pause().stop().destroy();controller.pause().stop().destroy()
        DjiCameraController.publish(DjiCameraState())
    }
}
