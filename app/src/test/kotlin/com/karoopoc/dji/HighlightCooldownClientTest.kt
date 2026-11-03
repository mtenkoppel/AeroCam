package com.karoopoc.dji

import android.os.Looper
import android.os.SystemClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Exercise the real operation queue, substituting only the Bluetooth write result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class HighlightCooldownClientTest {
    private lateinit var client: DjiOsmoBleClient
    private fun field(name: String) = DjiOsmoBleClient::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun count() = field("highlightCount").getInt(client)
    @Suppress("UNCHECKED_CAST")
    private fun writes() = field("writeQueue").get(client) as ArrayDeque<Any>
    private fun completeWrite(success: Boolean) {
        val write = writes().removeFirst()
        val callback = write.javaClass.getDeclaredField("onComplete").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (callback.get(write) as (Boolean) -> Unit)(success)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
    }
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        client = DjiOsmoBleClient(context, {})
        field("protocolConnected").setBoolean(client, true)
        field("lastCameraRxAtMs").setLong(client, SystemClock.elapsedRealtime())
        field("latestRecording").setBoolean(client, true)
        // Hold writes at the transport boundary; tests report their result explicitly.
        field("writeInProgress").setBoolean(client, true)
    }

    @Test fun simultaneousThresholdRequestsProduceOneSendAndNoDeferredMarker() {
        client.addHighlight("speed", true)
        client.addHighlight("power", true)
        idle()
        assertEquals(1, writes().size)
        completeWrite(true)
        assertEquals(1, count())
        assertTrue(writes().isEmpty())
        assertTrue((field("operationQueue").get(client) as List<*>).isEmpty())
        client.addHighlight("heart rate", true)
        idle()
        assertTrue(writes().isEmpty())
        assertEquals(1, count())
    }

    @Test fun manualMarkerIsNeverReplacedOrSuppressedByAutomaticRequests() {
        client.addHighlight("speed", true)
        client.addHighlight("manual")
        client.addHighlight("power", true)
        idle()
        completeWrite(true)
        assertEquals(1, writes().size)
        completeWrite(true)
        assertEquals(2, count())
        assertTrue(writes().isEmpty())
    }

    @Test fun failedSendDoesNotSuppressNextThreshold() {
        client.addHighlight("speed", true)
        client.addHighlight("power", true)
        idle()
        completeWrite(false)
        assertEquals(0, count())
        assertEquals(1, writes().size)
        completeWrite(true)
        assertEquals(1, count())
    }

    @Test fun offAllowsBackToBackAutomaticSends() {
        DjiSettings.setInt(RuntimeEnvironment.getApplication(), DjiSettings.KEY_HIGHLIGHT_COOLDOWN_SECONDS, 0)
        client.addHighlight("speed", true)
        client.addHighlight("power", true)
        idle()
        completeWrite(true)
        completeWrite(true)
        assertEquals(2, count())
    }

    @Test fun stoppedRecordingCancelsQueuedAutomaticMarker() {
        client.addHighlight("manual")
        client.addHighlight("speed", true)
        idle()
        field("latestRecording").setBoolean(client, false)
        completeWrite(false)
        assertTrue(writes().isEmpty())
        assertEquals(0, count())
    }
}
