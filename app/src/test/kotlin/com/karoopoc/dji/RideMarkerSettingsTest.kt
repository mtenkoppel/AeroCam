package com.karoopoc.dji

import android.content.Context
import com.karoopoc.OnboardingActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class RideMarkerSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val rawPrefs get() = context.getSharedPreferences(DjiSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun installTimes(first: Long, updated: Long) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime = first
        info.lastUpdateTime = updated
        shadowOf(context.packageManager).installPackage(info)
    }

    @Before fun freshInstall() {
        rawPrefs.edit().clear().commit()
        installTimes(100, 100)
    }

    @Test fun freshDefaultStaysOffAcrossLaterAppUpdatesAndReset() {
        assertFalse(DjiSettings.read(context).rideBookmarks)
        installTimes(100, 200)
        assertFalse(DjiSettings.read(context).rideBookmarks)
        DjiSettings.setBoolean(context, DjiSettings.KEY_RIDE_BOOKMARKS, true)
        DjiSettings.resetAll(context)
        assertFalse(DjiSettings.read(context).rideBookmarks)
    }

    @Test fun upgradedInstallWithoutSavedKeyPreservesTheOldOnDefault() {
        installTimes(100, 200)
        rawPrefs.edit().putString("camera_address", "test-pairing").putInt("unrelated-setting", 42).commit()
        assertTrue(DjiSettings.read(context).rideBookmarks)
        assertTrue(rawPrefs.getBoolean(DjiSettings.KEY_RIDE_BOOKMARKS, false))
        assertEquals("test-pairing", rawPrefs.getString("camera_address", null))
        assertEquals(42, rawPrefs.getInt("unrelated-setting", 0))
        assertTrue(DjiSettings.read(context).rideBookmarks)
    }

    @Test fun explicitChoicesWinForBothNewAndExistingInstallations() {
        for (updated in listOf(100L, 200L)) for (choice in listOf(false, true)) {
            rawPrefs.edit().clear().putBoolean(DjiSettings.KEY_RIDE_BOOKMARKS, choice).commit()
            installTimes(100, updated)
            assertEquals(choice, DjiSettings.read(context).rideBookmarks)
        }
    }

    @Test fun restoredOnboardingOrConfirmedPairingPreservesTheOldImplicitDefault() {
        for (key in listOf(OnboardingActivity.DONE, OnboardingActivity.DRAFT, "protocol_connected_once")) {
            rawPrefs.edit().clear().apply()
            if (key == OnboardingActivity.DRAFT) rawPrefs.edit().putString(key, "{}").commit()
            else rawPrefs.edit().putBoolean(key, true).commit()
            assertTrue(DjiSettings.read(context).rideBookmarks)
        }
    }

    @Test fun prepareOnboardingDoesNotOverwriteMigratedOrExplicitBookmarks() {
        for (choice in listOf(false, true)) {
            rawPrefs.edit().clear().putBoolean(DjiSettings.KEY_RIDE_BOOKMARKS, choice).commit()
            OnboardingActivity.prepare(context)
            assertEquals(choice, DjiSettings.read(context).rideBookmarks)
        }
    }
}
