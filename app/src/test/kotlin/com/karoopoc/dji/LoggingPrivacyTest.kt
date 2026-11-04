package com.karoopoc.dji

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Looper
import android.util.Log
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.UUID

/** Exercise production log paths with synthetic data, without a debug-only privacy branch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class LoggingPrivacyTest {
    private lateinit var client: DjiOsmoBleClient
    private fun field(name: String) = DjiOsmoBleClient::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun invoke(name: String, vararg args: Any?) =
        DjiOsmoBleClient::class.java.declaredMethods.single { it.name == name }.apply { isAccessible = true }
            .invoke(client, *args)
    private fun logs() = ShadowLog.getLogs().joinToString("\n") { it.msg }
    private fun assertAbsent(vararg values: String) {
        values.forEach { assertFalse("Sensitive synthetic value reached Logcat", logs().contains(it)) }
    }

    @Before fun setup() {
        DjiSettings.resetAll(RuntimeEnvironment.getApplication())
        client = DjiOsmoBleClient(RuntimeEnvironment.getApplication(), {})
        ShadowLog.clear()
        ShadowLog.setLoggable("AeroCamStatusRaw", Log.VERBOSE)
    }

    @Test fun pairingAndMalformedFramesDoNotLogIdentifiersOrPayloads() {
        val payload = DjiProtocol.connectionRequestPayload(byteArrayOf(91, 82, 73, 64, 55, 46), 1, 8765)
        val frame = DjiProtocol.createFrame(0, 0x19, 2, payload, 7)
        invoke("handleNotification", frame)
        val invalid = "synthetic-private-packet".toByteArray()
        invoke("handleNotification", invalid)
        assertTrue(logs().contains("verifyMode=1"))
        assertTrue(logs().contains("not a valid DJI R SDK frame"))
        assertAbsent("8765", DjiProtocol.hex(payload), DjiProtocol.hex(frame), DjiProtocol.hex(invalid), "12345678")
    }

    @Test fun verboseTagCannotEnableRawCameraStatusLogging() {
        val payload = ByteArray(38)
        payload[0] = 1
        payload[37] = 75
        val frame = DjiProtocol.createFrame(0x1d, 2, 0, payload, 8)
        invoke("handleNotification", frame)
        assertEquals(75, client.state().batteryPercent)
        assertTrue(ShadowLog.getLogsForTag("AeroCamStatusRaw").isEmpty())
        assertAbsent(DjiProtocol.hex(frame), DjiProtocol.hex(payload))
    }

    @Test fun transmittedGpsAndHandshakeFramesAreNotLogged() {
        val context = RuntimeEnvironment.getApplication()
        val gatt = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:11:22:33:44:55")
            .connectGatt(context, false, object : BluetoothGattCallback() {})
        field("gatt").set(client, gatt)
        field("writeCharacteristic").set(client, BluetoothGattCharacteristic(
            UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb"),
            BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE,
        ))
        field("lastGpsTxLogAtMs").setLong(client, -10_000)
        val gps = DjiProtocol.gpsPayload(DjiGpsTelemetry(12.3456789, 98.7654321, 123.0,
            1.0, 2.0, 0.0, 10.0, 10.0, 1.0, 10))
        val pairing = DjiProtocol.connectionRequestPayload(byteArrayOf(91, 82, 73, 64, 55, 46), 1, 8765)
        ShadowLog.clear()
        try {
            for ((label, cmd, payload) in listOf(Triple("real GPS data push", 0x17, gps), Triple("connection request", 0x19, pairing))) {
                field("writeInProgress").setBoolean(client, false)
                invoke("queueFrame", label, 0, cmd, 0, payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, 9, null, { _: Boolean -> })
                shadowOf(Looper.getMainLooper()).idle()
                assertTrue(logs().contains("DJI TX $label"))
                assertAbsent(DjiProtocol.hex(payload), DjiProtocol.hex(DjiProtocol.createFrame(0, cmd, 0, payload, 9)))
            }
            assertAbsent("12.3456789", "98.7654321", "02:11:22:33:44:55", "8765")
        } finally {
            gatt.close()
        }
    }

    @Test fun serviceKeepsPairingStatusInUiStateWithoutCopyingItToLogcat() {
        val service = Robolectric.buildService(DjiCameraService::class.java).create()
        try {
            client = DjiCameraService::class.java.getDeclaredField("client").apply { isAccessible = true }
                .get(service.get()) as DjiOsmoBleClient
            val message = "DJI handshake submitted; approve code 8765 on camera if prompted"
            ShadowLog.clear()
            invoke("logStatus", message)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(message, client.state().lastMessage)
            assertAbsent("8765", message)
        } finally {
            service.destroy()
        }
    }
}
