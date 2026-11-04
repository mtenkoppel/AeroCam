package com.karoopoc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.karoopoc.widget.DjiCameraStatusDataType
import com.karoopoc.widget.DjiVideoPhotoDataType
import com.karoopoc.widget.DjiPhotoDataType
import com.karoopoc.widget.DjiPowerDataType
import com.karoopoc.widget.DjiWidgetController
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiKarooIntegration
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.ManufacturerInfo
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnManufacturerInfo

class KarooPocExtension : KarooExtension("karoo-poc", "1.20") {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val karooSystem by lazy { KarooSystemService(this) }
    private var scanCallback: ScanCallback? = null
    private val seenDevices = mutableSetOf<String>()
    private var karooIntegration: DjiKarooIntegration? = null

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            DjiCameraStatusDataType(extension),
            DjiPowerDataType(extension),
            DjiPhotoDataType(extension),
            DjiVideoPhotoDataType(extension),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Karoo extension service created")
        karooSystem.connect { connected ->
            Log.i(TAG, "KarooSystem connected=$connected")
            if (connected) {
                if (karooIntegration == null) {
                    karooIntegration = DjiKarooIntegration(this, karooSystem).also { it.start() }
                }
                mainHandler.postDelayed({ DjiCameraController.connect(this) }, 600L)
            }
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Karoo extension service destroying")
        stopScan()
        karooIntegration?.stop()
        karooIntegration = null
        DjiWidgetController.disconnect()
        karooSystem.disconnect()
        super.onDestroy()
    }

    override fun onBonusAction(actionId: String) {
        Log.i(TAG, "DJI bonus action=$actionId")
        if (actionId != BONUS_SETTINGS) vibrate()
        when (actionId) {
            BONUS_RECORD -> DjiCameraController.toggleRecording(this)
            BONUS_HIGHLIGHT -> DjiCameraController.addHighlight(this)
            BONUS_SETTINGS -> startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(45L)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan(emitter: Emitter<Device>) {
        Log.i(TAG, "Karoo extension startScan requested")

        if (!hasBlePermission()) {
            Log.i(TAG, "Karoo extension scan skipped: BLE permissions missing")
            emitter.onError(IllegalStateException("BLE permissions missing"))
            return
        }

        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.i(TAG, "Karoo extension scan skipped: scanner unavailable")
            emitter.onError(IllegalStateException("BLE scanner unavailable"))
            return
        }

        stopScan()
        seenDevices.clear()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emitDjiDevice(result, emitter)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { emitDjiDevice(it, emitter) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.i(TAG, "Karoo extension DJI scan failed errorCode=$errorCode")
                emitter.onError(IllegalStateException("DJI scan failed: $errorCode"))
            }
        }

        scanCallback = callback
        scanner.startScan(callback)
        emitter.setCancellable {
            Log.i(TAG, "Karoo extension startScan cancelled")
            stopScan()
        }

        mainHandler.postDelayed({
            if (scanCallback == callback) {
                Log.i(TAG, "Karoo extension DJI scan completed")
                stopScan()
                emitter.onComplete()
            }
        }, EXTENSION_SCAN_MS)
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Log.i(TAG, "Karoo extension connectDevice requested")

        emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
        emitter.onNext(
            OnManufacturerInfo(
                ManufacturerInfo(
                    manufacturer = "DJI",
                    modelNumber = "Osmo Action",
                    serialNumber = uid,
                ),
            ),
        )

        val cancel = DjiWidgetController.observe(this) { state ->
            val status = when {
                state.camera.connection == CameraConnectionState.CONNECTED -> ConnectionStatus.CONNECTED
                state.camera.connection == CameraConnectionState.SEARCHING ||
                    state.camera.connection == CameraConnectionState.CONNECTING -> ConnectionStatus.SEARCHING
                else -> ConnectionStatus.DISCONNECTED
            }
            emitter.onNext(OnConnectionStatus(status))
        }
        DjiWidgetController.connect(this)

        emitter.setCancellable {
            Log.i(TAG, "Karoo extension connectDevice cancelled")
            cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private fun emitDjiDevice(result: ScanResult, emitter: Emitter<Device>) {
        val manufacturerData = parseDjiAdvertisement(result.scanRecord) ?: return
        val address = result.device.address
        if (!seenDevices.add(address)) return

        val name = result.scanRecord?.deviceName ?: result.device.name ?: "DJI Osmo Action"
        Log.i(
            TAG,
            "Karoo extension found DJI advertisement rssi=${result.rssi}",
        )

        emitter.onNext(
            Device(
                extension = extension,
                uid = address,
                dataTypes = listOf(
                    DataType.dataTypeId(extension, DjiCameraStatusDataType.TYPE_ID),
                    DataType.dataTypeId(extension, DjiPowerDataType.TYPE_ID),
                    DataType.dataTypeId(extension, DjiPhotoDataType.TYPE_ID),
                    DataType.dataTypeId(extension, DjiVideoPhotoDataType.TYPE_ID),
                ),
                displayName = name,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback)
        scanCallback = null
    }

    private fun hasBlePermission(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun parseDjiAdvertisement(scanRecord: ScanRecord?): ByteArray? {
        val raw = scanRecord?.bytes
        if (raw != null) {
            var index = 0
            while (index < raw.size) {
                val length = raw[index].toUByteInt()
                if (length == 0 || index + length >= raw.size) break

                val type = raw[index + 1].toUByteInt()
                val dataOffset = index + 2
                val dataLength = length - 1
                if (type == 0xFF && dataLength >= 5) {
                    val data = raw.copyOfRange(dataOffset, dataOffset + dataLength)
                    if (isDjiManufacturerData(data)) return data
                }
                index += length + 1
            }
        }

        val manufacturerData = scanRecord?.manufacturerSpecificData ?: return null
        for (i in 0 until manufacturerData.size()) {
            val companyId = manufacturerData.keyAt(i)
            val data = manufacturerData.valueAt(i)
            val combined = ByteArray(data.size + 2)
            combined[0] = (companyId and 0xFF).toByte()
            combined[1] = ((companyId ushr 8) and 0xFF).toByte()
            data.copyInto(combined, destinationOffset = 2)
            if (isDjiManufacturerData(combined)) return combined
        }
        return null
    }

    private fun isDjiManufacturerData(data: ByteArray): Boolean {
        return data.size >= 5 &&
            data[0].toUByteInt() == 0xAA &&
            data[1].toUByteInt() == 0x08 &&
            data[4].toUByteInt() == 0xFA
    }

    private fun Byte.toUByteInt(): Int = toInt() and 0xFF

    private fun ByteArray.toHex(): String {
        return joinToString(" ") { "%02X".format(it.toUByteInt()) }
    }

    companion object {
        private const val TAG = "KarooPoC"
        private const val EXTENSION_SCAN_MS = 15_000L
        private const val BONUS_RECORD = "dji-record"
        private const val BONUS_HIGHLIGHT = "dji-highlight"
        private const val BONUS_SETTINGS = "dji-settings"
    }
}
