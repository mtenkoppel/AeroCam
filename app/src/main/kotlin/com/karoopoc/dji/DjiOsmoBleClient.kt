package com.karoopoc.dji

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class DjiOsmoBleClient(
    context: Context,
    private val status: (String) -> Unit,
    private val requestBluetooth: (String) -> Unit = {},
    private val stateChanged: (DjiCameraState) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = appContext.getSharedPreferences("dji_osmo_poc", Context.MODE_PRIVATE)

    @Volatile private var diagnosticConnectionPaused = false

    private var scanCallback: ScanCallback? = null
    private var bestCandidate: DjiCandidate? = null
    private var gatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var protocolConnected = false
    private var cameraDeviceId: Long? = prefs.takeIf { it.contains(KEY_CAMERA_DEVICE_ID) }
        ?.getLong(KEY_CAMERA_DEVICE_ID, 0L)
    private var seq = 0
    private var writeInProgress = false
    private var connectionRejectionHandled = false
    private var scanTargetAddress: String? = null
    private val pendingActionsAfterConnect = ArrayDeque<PendingCameraAction>()
    private var reconnectEnabled = false
    private var reconnectScheduled = false
    private var lastCameraRxAtMs = 0L
    private var lastCameraStatusLogAtMs = 0L
    private var lastCameraStatusKey: String? = null
    private var lastGpsTxLogAtMs = 0L
    private var gpsStreaming = false
    private var latestGpsTelemetry: DjiGpsTelemetry? = null
    private var keepAwakeEnabled = false
    private var keepAwakeIntervalMs = DEFAULT_KEEP_AWAKE_INTERVAL_SECONDS * 1_000L
    private var keepAwakeQsCount = 0
    private var keepAwakeScheduled = false
    private var keepAwakeGeneration = 0L
    private var keepAwakeAttempt: KeepAwakeAttempt? = null
    private var keepAwakeFaulted = false
    private var keepAwakeTimeout: Runnable? = null
    private fun rideEvent(name: String, details: String = "") = RideDiagnostics.event(appContext, name, details)
    private var latestBatteryPercent: Int? = null
    private var latestCameraStatus: Int? = null
    private var latestCameraMode: Int? = null
    private var latestVideoResolution: Int? = null
    private var latestVideoFps: Int? = null
    private var latestRecording = false
    private val highlightCooldown = HighlightCooldown()
    private var highlightCount = 0
    private var highlightFeedback: String? = null
    private var pendingHighlightFeedback: String? = null
    private var highlightFeedbackRunnable: Runnable? = null
    private var sleepOfflineAssumed = false
    private var sleepShutdownRunnable: Runnable? = null
    private var reconnectAttempt = 0
    private var rideActive = false
    private var photoState = PhotoCaptureState.IDLE
    private var photoCompletedRunnable: Runnable? = null
    private val seenDjiAddresses = mutableSetOf<String>()
    private val writeQueue = ArrayDeque<PendingWrite>()
    private val operationQueue = mutableListOf<CameraOperation>()
    private var activeWrite: PendingWrite? = null
    private var activeOperation: ActiveOperation? = null
    private var operationOrder = 0L
    private var cameraState = DjiCameraState(configured = hasKnownDevice())
    private val livenessCheck = object : Runnable {
        override fun run() {
            if (!protocolConnected) return

            val ageMs = SystemClock.elapsedRealtime() - lastCameraRxAtMs
            val timeoutMs = if (keepAwakeEnabled) KEEP_AWAKE_LIVENESS_TIMEOUT_MS else LIVENESS_TIMEOUT_MS
            if (ageMs > timeoutMs) {
                rideEvent("LINK_STALE", "rxAgeMs=$ageMs")
                logStatus("KNOWN_DEVICE: connection stale (${ageMs / 1000}s without camera data)")
                disconnectInternal(clearReconnect = false)
                scheduleReconnect("stale link", RECONNECT_AFTER_LOSS_MS)
                return
            }

            mainHandler.postDelayed(this, LIVENESS_CHECK_MS)
        }
    }
    private val keepAwakeQs = object : Runnable {
        override fun run() {
            keepAwakeScheduled = false
            if (!shouldKeepAwake()) {
                publishState()
                return
            }
            if (!isProtocolLinkFresh()) {
                logStatus("DJI keep awake: reconnecting before next QS press")
                autoConnectKnownDevice()
                return
            }
            beginKeepAwakeAttempt()
        }
    }
    private val gpsPush = object : Runnable {
        override fun run() {
            if (!protocolConnected || !gpsStreaming) return
            val telemetry = latestGpsTelemetry
            if (telemetry == null) {
                mainHandler.postDelayed(this, GPS_PUSH_INTERVAL_MS)
                return
            }

            enqueueOperation(
                label = LABEL_GPS,
                priority = CommandPriority.TELEMETRY,
                coalesceKey = LABEL_GPS,
                steps = listOf(
                    CommandStep(
                        label = LABEL_GPS,
                        cmdSet = DjiProtocol.CMD_SET_COMMON,
                        cmdId = DjiProtocol.CMD_ID_GPS_DATA_PUSH,
                        cmdType = DjiProtocol.CMD_NO_RESPONSE,
                        payload = DjiProtocol.gpsPayload(telemetry),
                        writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                    ),
                ),
            )

            mainHandler.postDelayed(this, GPS_PUSH_INTERVAL_MS)
        }
    }

    fun hasKnownDevice(): Boolean {
        return prefs.getBoolean(KEY_PROTOCOL_CONNECTED_ONCE, false) &&
            prefs.getString(KEY_CAMERA_ADDRESS, null) != null
    }

    fun connectionState(): ConnectionState {
        return when {
            !hasKnownDevice() -> ConnectionState.NOT_CONFIGURED
            isProtocolLinkFresh() -> ConnectionState.CONNECTED
            else -> ConnectionState.DISCONNECTED
        }
    }

    fun state(): DjiCameraState = cameraState

    fun setRideActive(active: Boolean) {
        rideActive = active
        if (active && reconnectEnabled && !isProtocolLinkFresh()) {
            scheduleReconnect("ride became active", FAST_RECONNECT_DELAY_MS)
        }
    }

    fun updateGpsTelemetry(telemetry: DjiGpsTelemetry) {
        latestGpsTelemetry = telemetry
        if (protocolConnected) startGpsStream()
    }

    fun knownDeviceSummary(): String {
        val name = prefs.getString(KEY_CAMERA_NAME, null)
        val address = prefs.getString(KEY_CAMERA_ADDRESS, null)
        return if (hasKnownDevice() && address != null) {
            "KNOWN_DEVICE: ${name ?: "unnamed"} $address"
        } else {
            "NO_KNOWN_DEVICE: connect a DJI camera"
        }
    }

    @SuppressLint("MissingPermission")
    fun autoConnectKnownDevice() {
        if (diagnosticConnectionPaused) return
        sleepOfflineAssumed = false
        sleepShutdownRunnable?.let(mainHandler::removeCallbacks)
        sleepShutdownRunnable = null
        reconnectEnabled = true

        if (isProtocolLinkFresh()) {
            logStatus("KNOWN_DEVICE: already connected")
            return
        } else if (protocolConnected) {
            logStatus("KNOWN_DEVICE: stale connection; reconnecting")
            disconnectInternal(clearReconnect = false)
        }

        if (scanCallback != null || gatt != null) {
            Log.i(TAG, "DJI auto-connect already in progress")
            return
        }

        val address = prefs.getString(KEY_CAMERA_ADDRESS, null)
        if (!hasKnownDevice() || address == null) {
            logStatus("NO_KNOWN_DEVICE: pair with Find DJI + Handshake")
            return
        }

        val adapter = bluetoothManager?.adapter
        if (!hasBlePermission()) {
            logStatus("KNOWN_DEVICE: BLE permissions are missing")
            return
        }
        if (adapter == null) {
            logStatus("KNOWN_DEVICE: BLE adapter unavailable")
            recoverBluetooth("adapter unavailable")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            logStatus("KNOWN_DEVICE: BLE scanner unavailable; adapterState=${adapter.state} enabled=${adapter.isEnabled}")
            recoverBluetooth("scanner unavailable state=${adapter.state}")
            return
        }

        disconnectInternal(clearReconnect = false)
        bestCandidate = null
        seenDjiAddresses.clear()
        connectionRejectionHandled = false
        scanTargetAddress = address

        Log.i(TAG, "DJI auto-connect scan started")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                scanCallback = null
                logStatus("KNOWN_DEVICE: scan failed errorCode=$errorCode")
                publishState(connection = CameraConnectionState.DISCONNECTED)
                scheduleReconnect("scan failed", reconnectDelayMs())
            }
        }

        scanCallback = callback
        try {
            scanner.startScan(callback)
        } catch (error: RuntimeException) {
            scanCallback = null
            logStatus("KNOWN_DEVICE: unable to start BLE scan: ${error.javaClass.simpleName}")
            recoverBluetooth("scan start failed")
            return
        }
        logStatus("KNOWN_DEVICE: searching for $address")
        publishState(connection = CameraConnectionState.SEARCHING)

        mainHandler.postDelayed({
            if (!protocolConnected && scanTargetAddress == address) {
                stopScan()
                bestCandidate = null
                scanTargetAddress = null
                logStatus("KNOWN_DEVICE: not found; turn camera on or open Bluetooth Remote Controller")
                publishState(connection = CameraConnectionState.DISCONNECTED)
                reconnectAttempt += 1
                scheduleReconnect("known device not found", reconnectDelayMs())
            }
        }, SAVED_SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    fun startDiscoveryAndHandshake() {
        if (diagnosticConnectionPaused) return
        sleepOfflineAssumed = false
        sleepShutdownRunnable?.let(mainHandler::removeCallbacks)
        sleepShutdownRunnable = null
        if (hasKnownDevice()) {
            logStatus("KNOWN_DEVICE: Find DJI + Handshake disabled; forget device first")
            return
        }

        if (!hasBlePermission()) {
            logStatus("NO_KNOWN_DEVICE: BLE permissions missing")
            return
        }

        val adapter = bluetoothManager?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null) {
            logStatus("NO_KNOWN_DEVICE: BLE scanner unavailable; adapterState=${adapter?.state} enabled=${adapter?.isEnabled}")
            return
        }

        reconnectEnabled = true
        disconnectInternal(clearReconnect = false)
        bestCandidate = null
        seenDjiAddresses.clear()
        connectionRejectionHandled = false
        scanTargetAddress = null
        pendingActionsAfterConnect.clear()

        val savedAddress = prefs.getString(KEY_CAMERA_ADDRESS, null)
        Log.i(TAG, "DJI scan started")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::handleScanResult)
            }

            override fun onScanFailed(errorCode: Int) {
                scanCallback = null
                logStatus("NO_KNOWN_DEVICE: DJI scan failed errorCode=$errorCode")
            }
        }

        scanCallback = callback
        scanner.startScan(callback)
        logStatus("NO_KNOWN_DEVICE: DJI scan running for ${SCAN_DURATION_MS / 1000}s")
        publishState(connection = CameraConnectionState.SEARCHING)

        mainHandler.postDelayed({
            stopScanAndConnectBest()
        }, SCAN_DURATION_MS)
    }

    fun triggerPhoto() {
        if (!runWhenReady(PendingCameraAction.PHOTO)) {
            return
        }

        triggerPhotoNow()
    }

    fun startVideoRecording() {
        if (!runWhenReady(PendingCameraAction.START_VIDEO)) {
            return
        }

        startVideoRecordingNow()
    }

    fun stopVideoRecording() {
        if (!runWhenReady(PendingCameraAction.STOP_VIDEO)) {
            return
        }

        stopVideoRecordingNow()
    }

    fun sleepCamera() {
        setKeepAwake(false, keepAwakeIntervalMs.toInt() / 1_000)
        if (!runWhenReady(PendingCameraAction.SLEEP)) {
            return
        }

        sleepCameraNow()
    }

    fun addHighlight(feedback: String? = null, cooldownLimited: Boolean = false) {
        if (!latestRecording) {
            logStatus("DJI highlight skipped: camera is not recording")
            return
        }
        if (cooldownLimited) {
            // Threshold moments are not replayed after reconnecting, and cannot
            // overwrite an exempt highlight waiting for the connection.
            if (!isProtocolLinkFresh()) {
                logStatus("DJI automatic highlight skipped: camera connection is not ready")
                return
            }
            addHighlightNow(feedback, cooldownLimited = true)
            return
        }
        pendingHighlightFeedback = feedback
        if (!runWhenReady(PendingCameraAction.HIGHLIGHT)) {
            return
        }

        addHighlightNow(pendingHighlightFeedback.also { pendingHighlightFeedback = null })
    }

    fun toggleMode() {
        if (latestRecording) {
            logStatus("DJI mode switch skipped: camera is recording")
            return
        }
        if (!runWhenReady(PendingCameraAction.TOGGLE_MODE)) return
        toggleModeNow()
    }

    fun setKeepAwake(enabled: Boolean, intervalSeconds: Int) {
        val wasEnabled = keepAwakeEnabled
        keepAwakeFaulted = false
        rideEvent("KEEP_AWAKE_SETTING", "enabled=$enabled intervalSeconds=$intervalSeconds")
        keepAwakeEnabled = enabled
        keepAwakeIntervalMs = intervalSeconds.coerceIn(
            MIN_KEEP_AWAKE_INTERVAL_SECONDS,
            MAX_KEEP_AWAKE_INTERVAL_SECONDS,
        ) * 1_000L
        if (!enabled) {
            cancelKeepAwake()
            operationQueue.removeAll { it.coalesceKey == KEEP_AWAKE_OPERATION_KEY }
        } else {
            cancelKeepAwake()
            if (protocolConnected && !latestRecording) {
                scheduleKeepAwakePulse(
                    delayMs = if (wasEnabled) keepAwakeIntervalMs else 0L,
                    reason = if (wasEnabled) "settings updated" else "enabled",
                )
            } else {
                publishState()
            }
        }
        logStatus(
            "DJI keep awake ${if (enabled) "enabled" else "disabled"}; " +
                "interval=${keepAwakeIntervalMs / 1_000}s",
        )
        publishState()
    }

    private fun triggerPhotoNow() {
        photoState = PhotoCaptureState.REQUESTED
        publishState()

        if (latestRecording) {
            logStatus("DJI photo trigger: sending snapshot while recording")
            enqueueOperation(
                label = "photo snapshot while recording",
                priority = CommandPriority.USER_ACTION,
                steps = listOf(keyReportStep(DjiProtocol.snapshotButtonShortPressPayload())),
                onComplete = ::completePhotoWithoutStatus,
            )
            return
        }

        val commandDeviceId = DjiProtocol.cameraCommandDeviceId(cameraDeviceId)
        logStatus("DJI photo trigger queued: switch to Photo then press Record")
        enqueueOperation(
            label = "take photo",
            priority = CommandPriority.USER_ACTION,
            steps = listOf(
                CommandStep(
                    label = "mode switch to Photo",
                    cmdSet = DjiProtocol.CMD_SET_CAMERA,
                    cmdId = DjiProtocol.CMD_ID_MODE_SWITCH,
                    cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
                    payload = DjiProtocol.photoModePayload(commandDeviceId),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    delayAfterMs = 700L,
                ),
                keyReportStep(DjiProtocol.recordButtonShortPressPayload(), "photo shutter press"),
            ),
            onComplete = {
                if (photoState == PhotoCaptureState.REQUESTED) {
                    completePhotoWithoutStatus()
                }
            },
        )
    }

    private fun addHighlightNow(feedback: String? = null, cooldownLimited: Boolean = false) {
        if (!latestRecording) {
            logStatus("DJI highlight cancelled: recording is no longer active")
            return
        }
        logStatus("DJI highlight queued")
        enqueueOperation(
            label = "recording highlight",
            priority = CommandPriority.USER_ACTION,
            coalesceKey = if (cooldownLimited) AUTO_HIGHLIGHT_OPERATION_KEY else HIGHLIGHT_OPERATION_KEY,
            cooldownLimitedHighlight = cooldownLimited,
            steps = listOf(keyReportStep(DjiProtocol.qsButtonShortPressPayload(), "highlight QS press")),
            onComplete = {
                highlightCooldown.onSent(SystemClock.elapsedRealtime())
                highlightCount += 1
                showHighlightFeedback(feedback ?: "HIGHLIGHT")
                logStatus("DJI highlight sent")
            },
        )
    }

    private fun toggleModeNow() {
        val commandDeviceId = DjiProtocol.cameraCommandDeviceId(cameraDeviceId)
        val switchToPhoto = latestCameraMode != CAMERA_MODE_PHOTO
        logStatus("DJI mode switch queued: ${if (switchToPhoto) "Photo" else "Video"}")
        enqueueOperation(
            label = "toggle camera mode",
            priority = CommandPriority.USER_ACTION,
            coalesceKey = MODE_OPERATION_KEY,
            steps = listOf(
                CommandStep(
                    label = if (switchToPhoto) "mode switch to Photo" else "mode switch to Video",
                    cmdSet = DjiProtocol.CMD_SET_CAMERA,
                    cmdId = DjiProtocol.CMD_ID_MODE_SWITCH,
                    cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
                    payload = if (switchToPhoto) {
                        DjiProtocol.photoModePayload(commandDeviceId)
                    } else {
                        DjiProtocol.videoModePayload(commandDeviceId)
                    },
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            ),
        )
    }

    private fun sleepCameraNow() {
        logStatus("DJI sleep: requesting protocol power mode 3")
        sleepOfflineAssumed = true
        reconnectEnabled = false
        reconnectScheduled = false
        pendingActionsAfterConnect.clear()
        mainHandler.removeCallbacks(livenessCheck)
        publishState(
            connection = CameraConnectionState.DISCONNECTED,
            displayOff = false,
            recording = false,
            recordingDurationSeconds = 0,
            lastMessage = "KNOWN_DEVICE: power off requested",
        )
        enqueueOperation(
            label = "power mode sleep",
            priority = CommandPriority.USER_ACTION,
            steps = listOf(
                CommandStep(
                    label = "power mode sleep",
                    cmdSet = DjiProtocol.CMD_SET_COMMON,
                    cmdId = DjiProtocol.CMD_ID_POWER_MODE_SWITCH,
                    cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
                    payload = DjiProtocol.sleepModePayload(),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ),
            ),
        )
        sleepShutdownRunnable?.let(mainHandler::removeCallbacks)
        sleepShutdownRunnable = Runnable { forceOfflineAfterSleep() }.also {
            mainHandler.postDelayed(it, SLEEP_OFFLINE_DELAY_MS)
        }
    }

    private fun startVideoRecordingNow() {
        val commandDeviceId = DjiProtocol.cameraCommandDeviceId(cameraDeviceId)
        logStatus("DJI recording start queued")
        enqueueOperation(
            label = "start recording",
            priority = CommandPriority.USER_ACTION,
            steps = listOf(
                CommandStep(
                    label = "mode switch to Video",
                    cmdSet = DjiProtocol.CMD_SET_CAMERA,
                    cmdId = DjiProtocol.CMD_ID_MODE_SWITCH,
                    cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
                    payload = DjiProtocol.videoModePayload(commandDeviceId),
                    writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                    delayAfterMs = 700L,
                ),
                recordControlStep(commandDeviceId, start = true),
            ),
        )
    }

    private fun stopVideoRecordingNow() {
        val commandDeviceId = DjiProtocol.cameraCommandDeviceId(cameraDeviceId)
        logStatus("DJI recording stop queued")
        enqueueOperation(
            label = "stop recording",
            priority = CommandPriority.USER_ACTION,
            steps = listOf(recordControlStep(commandDeviceId, start = false)),
        )
    }

    private fun runWhenReady(action: PendingCameraAction): Boolean {
        if (diagnosticConnectionPaused) return false
        if (isProtocolLinkFresh()) return true

        if (!hasKnownDevice()) {
            logStatus("NO_KNOWN_DEVICE: ${action.label} skipped; pair a camera first")
            return false
        }

        if (!pendingActionsAfterConnect.contains(action)) {
            pendingActionsAfterConnect.addLast(action)
        }
        reconnectEnabled = true
        if (protocolConnected) {
            logStatus("KNOWN_DEVICE: stale connection; reconnecting before ${action.label}")
            disconnectInternal(clearReconnect = false)
        } else {
            logStatus("KNOWN_DEVICE: searching before ${action.label}")
        }
        autoConnectKnownDevice()
        return false
    }

    private fun runPendingActionIfNeeded() {
        if (pendingActionsAfterConnect.isEmpty()) return
        mainHandler.postDelayed({
            while (pendingActionsAfterConnect.isNotEmpty() && isProtocolLinkFresh()) {
                when (pendingActionsAfterConnect.removeFirst()) {
                    PendingCameraAction.PHOTO -> triggerPhotoNow()
                    PendingCameraAction.START_VIDEO -> startVideoRecordingNow()
                    PendingCameraAction.STOP_VIDEO -> stopVideoRecordingNow()
                    PendingCameraAction.SLEEP -> sleepCameraNow()
                    PendingCameraAction.HIGHLIGHT -> addHighlightNow(
                        pendingHighlightFeedback.also { pendingHighlightFeedback = null },
                    )
                    PendingCameraAction.TOGGLE_MODE -> toggleModeNow()
                }
            }
        }, 900)
    }

    private fun startGpsStream() {
        if (gpsStreaming) return
        gpsStreaming = true
        Log.i(
            TAG,
            "DJI real GPS streaming started at 10Hz: DJI 0017 location, altitude and velocity fields",
        )
        mainHandler.post(gpsPush)
    }

    private fun stopGpsStream() {
        if (!gpsStreaming) return
        gpsStreaming = false
        mainHandler.removeCallbacks(gpsPush)
        operationQueue.removeAll { it.coalesceKey == LABEL_GPS }
        Log.i(TAG, "DJI real GPS streaming stopped")
    }

    // Temporary Settings-only diagnostic controls. Pause lasts for this client session.
    fun diagnosticDisconnect() {
        if (latestRecording || pendingActionsAfterConnect.isNotEmpty() ||
            operationQueue.any { it.priority == CommandPriority.USER_ACTION } ||
            activeOperation?.operation?.priority == CommandPriority.USER_ACTION
        ) {
            logStatus("Diagnostic disconnect blocked: recording or camera action pending")
            return
        }
        diagnosticConnectionPaused = true
        disconnectInternal(clearReconnect = true)
        latestRecording = false
        photoState = PhotoCaptureState.IDLE
        publishState(connection = CameraConnectionState.DISCONNECTED, recording = false,
            recordingDurationSeconds = 0, displayOff = false)
        logStatus("Diagnostic camera connection paused; Bluetooth remains enabled")
    }

    fun diagnosticReconnect() {
        diagnosticConnectionPaused = false
        logStatus("Diagnostic camera connection resumed")
        autoConnectKnownDevice()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        disconnectInternal(clearReconnect = true)
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal(clearReconnect: Boolean) {
        sleepShutdownRunnable?.let(mainHandler::removeCallbacks)
        sleepShutdownRunnable = null
        if (clearReconnect) {
            reconnectEnabled = false
            reconnectScheduled = false
            pendingActionsAfterConnect.clear()
        }
        stopScan()
        stopGpsStream()
        cancelKeepAwake()
        rideEvent("LINK_CLOSED", "automaticReconnect=${!clearReconnect}")
        mainHandler.removeCallbacks(livenessCheck)
        writeQueue.clear()
        writeInProgress = false
        activeWrite = null
        activeOperation = null
        if (clearReconnect) operationQueue.clear()
        protocolConnected = false
        notifyCharacteristic = null
        writeCharacteristic = null
        scanTargetAddress = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun forceOfflineAfterSleep() {
        Log.i(TAG, "DJI sleep: assuming camera is off and stopping connection monitoring")
        disconnectInternal(clearReconnect = true)
        publishState(
            connection = CameraConnectionState.DISCONNECTED,
            displayOff = false,
            recording = false,
            recordingDurationSeconds = 0,
        )
    }

    fun forgetKnownDevice() {
        disconnect()
        pendingActionsAfterConnect.clear()
        prefs.edit()
            .remove(KEY_CAMERA_ADDRESS)
            .remove(KEY_CAMERA_NAME)
            .remove(KEY_CAMERA_RSSI)
            .remove(KEY_LAST_ADVERTISEMENT)
            .remove(KEY_CONTROLLER_ID)
            .remove(KEY_LAST_VERIFY_DATA)
            .remove(KEY_LAST_VERIFY_MODE)
            .remove(KEY_CAMERA_DEVICE_ID)
            .remove(KEY_LAST_CAMERA_VERIFY_MODE)
            .remove(KEY_LAST_CAMERA_VERIFY_DATA)
            .remove(KEY_PROTOCOL_CONNECTED_ONCE)
            .remove(KEY_PRODUCT_ID)
            .remove(KEY_SDK_VERSION)
            .apply()
        cameraDeviceId = null
        latestBatteryPercent = null
        latestCameraStatus = null
        latestCameraMode = null
        latestRecording = false
        highlightCount = 0
        photoState = PhotoCaptureState.IDLE
        logStatus("Forgot DJI camera")
        publishState(
            connection = CameraConnectionState.NOT_CONFIGURED,
            batteryPercent = null,
            displayOff = false,
            recording = false,
            recordingDurationSeconds = 0,
            remainingCapacityMb = null,
            remainingRecordingSeconds = null,
        )
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        if (diagnosticConnectionPaused) return
        val advertisement = parseDjiAdvertisement(result.scanRecord) ?: return
        val name = result.scanRecord?.deviceName ?: result.device.name ?: "unnamed"
        val candidate = DjiCandidate(result.device, name, result.rssi, advertisement)
        val previousBest = bestCandidate
        val savedAddress = prefs.getString(KEY_CAMERA_ADDRESS, null)
        val targetAddress = scanTargetAddress

        if (targetAddress != null && result.device.address != targetAddress) {
            return
        }

        val firstTimeSeen = seenDjiAddresses.add(result.device.address)
        if (firstTimeSeen) {
            Log.i(
                TAG,
                "DJI advertisement beacon found rssi=${result.rssi}",
            )
        }

        saveCandidate(candidate)

        val isSavedCamera = savedAddress != null && result.device.address == savedAddress
        if (previousBest == null || isSavedCamera || result.rssi > previousBest.rssi) {
            bestCandidate = candidate
        }

        if (targetAddress != null && result.device.address == targetAddress) {
            logStatus("KNOWN_DEVICE: found $name; connecting")
            mainHandler.post { stopScanAndConnectBest() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanAndConnectBest() {
        if (diagnosticConnectionPaused) return
        if (gatt != null) return

        val candidate = bestCandidate
        if (candidate == null) {
            stopScan()
            if (hasKnownDevice()) {
                logStatus("KNOWN_DEVICE: no DJI advertisement found")
            } else {
                logStatus("NO_KNOWN_DEVICE: no DJI advertisement found")
            }
            publishState(connection = CameraConnectionState.DISCONNECTED)
            return
        }

        logStatus("DJI connecting to ${candidate.name} ${candidate.device.address}")
        publishState(connection = CameraConnectionState.CONNECTING)
        scanTargetAddress = null
        gatt = candidate.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        mainHandler.postDelayed({
            stopScan()
        }, CONNECT_SCAN_OVERLAP_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val callback = scanCallback ?: return
        bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback)
        scanCallback = null
    }

    private fun isProtocolLinkFresh(): Boolean {
        if (!protocolConnected) return false
        val ageMs = SystemClock.elapsedRealtime() - lastCameraRxAtMs
        return ageMs <= LIVENESS_TIMEOUT_MS
    }

    private fun scheduleReconnect(reason: String, delayMs: Long) {
        if (!reconnectEnabled || reconnectScheduled || !hasKnownDevice()) return
        reconnectScheduled = true
        Log.i(TAG, "DJI reconnect scheduled reason=$reason delayMs=$delayMs")
        mainHandler.postDelayed({
            reconnectScheduled = false
            if (reconnectEnabled && !isProtocolLinkFresh() && hasKnownDevice()) {
                autoConnectKnownDevice()
            }
        }, delayMs)
    }

    private fun recoverBluetooth(reason: String) {
        requestBluetooth(reason)
        publishState(connection = CameraConnectionState.DISCONNECTED)
        reconnectAttempt += 1
        scheduleReconnect("Bluetooth unavailable", BLUETOOTH_RECOVERY_DELAY_MS)
    }

    private fun reconnectDelayMs(): Long {
        return when {
            rideActive -> FAST_RECONNECT_DELAY_MS
            reconnectAttempt < FAST_RECONNECT_ATTEMPTS -> FAST_RECONNECT_DELAY_MS
            else -> SLOW_RECONNECT_DELAY_MS
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                if (gatt !== this@DjiOsmoBleClient.gatt) return@post
                if (diagnosticConnectionPaused) {
                    gatt.close()
                    return@post
                }
                Log.i(TAG, "DJI GATT connection state status=$status newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    stopScan()
                    logStatus("KNOWN_DEVICE: BLE connected; requesting MTU")
                    publishState(connection = CameraConnectionState.CONNECTING)
                    gatt.requestMtu(500)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    protocolConnected = false
                    rideEvent("LINK_DISCONNECTED", "gattStatus=$status")
                    stopGpsStream()
                    cancelKeepAwake()
                    latestRecording = false
                    photoState = PhotoCaptureState.IDLE
                    mainHandler.removeCallbacks(livenessCheck)
                    logStatus("KNOWN_DEVICE: BLE disconnected status=$status")
                    publishState(
                        connection = CameraConnectionState.DISCONNECTED,
                        displayOff = false,
                        recording = false,
                        recordingDurationSeconds = 0,
                    )
                    gatt.close()
                    if (this@DjiOsmoBleClient.gatt == gatt) {
                        this@DjiOsmoBleClient.gatt = null
                    }
                    scheduleReconnect("BLE disconnected", RECONNECT_AFTER_LOSS_MS)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "DJI MTU changed mtu=$mtu status=$status")
            gatt.discoverServices()
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.i(TAG, "DJI services discovered status=$status")
            val service = gatt.getService(UUID_SERVICE)
            if (service == null) {
                logStatus("DJI service FFF0 not found")
                logServices(gatt.services)
                return
            }

            notifyCharacteristic = service.getCharacteristic(UUID_NOTIFY)
            writeCharacteristic = service.getCharacteristic(UUID_WRITE)
            Log.i(TAG, "DJI notifyCharacteristic=${notifyCharacteristic != null} writeCharacteristic=${writeCharacteristic != null}")

            val notify = notifyCharacteristic
            if (notify == null || writeCharacteristic == null) {
                logStatus("DJI characteristics missing")
                return
            }

            gatt.setCharacteristicNotification(notify, true)
            val descriptor = notify.getDescriptor(UUID_CCCD)
            if (descriptor == null) {
                logStatus("DJI notify CCCD descriptor missing")
                return
            }

            val accepted = writeDescriptor(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            Log.i(TAG, "DJI enable notifications accepted=$accepted")
            if (!accepted) {
                logStatus("DJI notify enable write was rejected")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.i(TAG, "DJI descriptor write status=$status uuid=${descriptor.uuid}")
            if (descriptor.uuid == UUID_CCCD && status == BluetoothGatt.GATT_SUCCESS) {
                logStatus("DJI notifications enabled; submitting handshake")
                submitHandshake()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            mainHandler.post {
                if (gatt === this@DjiOsmoBleClient.gatt) handleNotification(value)
            }
        }

        @Deprecated("Used on Android versions before API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value?.copyOf() ?: ByteArray(0)
            mainHandler.post {
                if (gatt === this@DjiOsmoBleClient.gatt) handleNotification(value)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.i(TAG, "DJI write complete status=$status uuid=${characteristic.uuid}")
            mainHandler.post {
                if (gatt !== this@DjiOsmoBleClient.gatt) return@post
                activeWrite?.let { pending ->
                    completeActiveWrite(pending, status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }
    }

    private fun submitHandshake() {
        val verifyData = Random.nextInt(0, 10_000)
        val verifyMode = if (prefs.getBoolean(KEY_PROTOCOL_CONNECTED_ONCE, false)) 0 else 1
        val controllerId = controllerId()

        prefs.edit()
            .putInt(KEY_LAST_VERIFY_DATA, verifyData)
            .putInt(KEY_LAST_VERIFY_MODE, verifyMode)
            .putString(KEY_CONTROLLER_ID, DjiProtocol.hex(controllerId))
            .apply()

        Log.i(
            TAG,
            "DJI handshake request submitted verifyMode=$verifyMode",
        )
        logStatus("DJI handshake submitted; approve code $verifyData on camera if prompted")

        queueFrame(
            label = "connection request",
            cmdSet = DjiProtocol.CMD_SET_COMMON,
            cmdId = DjiProtocol.CMD_ID_CONNECTION_REQUEST,
            cmdType = DjiProtocol.CMD_WAIT_RESULT,
            payload = DjiProtocol.connectionRequestPayload(
                controllerId = controllerId,
                verifyMode = verifyMode,
                verifyData = verifyData,
            ),
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
    }

    private fun handleNotification(value: ByteArray) {
        if (diagnosticConnectionPaused) return
        if (value.isEmpty()) return

        val frame = DjiProtocol.parseFrame(value)
        if (frame == null) {
            Log.w(TAG, "DJI RX ignored: not a valid DJI R SDK frame length=${value.size}")
            return
        }

        lastCameraRxAtMs = SystemClock.elapsedRealtime()

        if (!frame.isRoutineStatusPush()) {
            Log.i(
                TAG,
                "DJI RX frame cmd=${frame.commandName} seq=${frame.seq} cmdType=0x%02X response=${frame.isResponse}"
                    .format(frame.cmdType),
            )
        }

        when (frame.cmdSet to frame.cmdId) {
            DjiProtocol.CMD_SET_COMMON to DjiProtocol.CMD_ID_CONNECTION_REQUEST -> handleConnectionFrame(frame)
            DjiProtocol.CMD_SET_COMMON to DjiProtocol.CMD_ID_VERSION_QUERY -> handleVersionFrame(frame)
            DjiProtocol.CMD_SET_CAMERA to DjiProtocol.CMD_ID_CAMERA_STATUS_PUSH -> handleCameraStatus(frame)
            DjiProtocol.CMD_SET_CAMERA to DjiProtocol.CMD_ID_NEW_CAMERA_STATUS_PUSH -> handleNewCameraStatus(frame)
            DjiProtocol.CMD_SET_COMMON to DjiProtocol.CMD_ID_KEY_REPORT -> {
                logReturnCode("key report", frame)
                handleKeepAwakeResponse(frame)
            }
            DjiProtocol.CMD_SET_COMMON to DjiProtocol.CMD_ID_GPS_DATA_PUSH -> logReturnCode("GPS data push", frame)
            DjiProtocol.CMD_SET_COMMON to DjiProtocol.CMD_ID_POWER_MODE_SWITCH -> logReturnCode("power mode", frame)
            DjiProtocol.CMD_SET_CAMERA to DjiProtocol.CMD_ID_MODE_SWITCH -> logReturnCode("mode switch", frame)
            DjiProtocol.CMD_SET_CAMERA to DjiProtocol.CMD_ID_RECORD_CONTROL -> logReturnCode("record control", frame)
            else -> Log.i(TAG, "DJI RX unhandled command=${frame.commandName}")
        }
    }

    private fun DjiProtocol.Frame.isRoutineStatusPush(): Boolean {
        return cmdSet == DjiProtocol.CMD_SET_CAMERA &&
            cmdId == DjiProtocol.CMD_ID_CAMERA_STATUS_PUSH
    }

    private fun handleConnectionFrame(frame: DjiProtocol.Frame) {
        if (frame.payload.size >= 33) {
            val deviceId = DjiProtocol.readU32(frame.payload, 0)
            val verifyMode = frame.payload[26].toUByteInt()
            val verifyData = DjiProtocol.readU16(frame.payload, 27)
            cameraDeviceId = deviceId

            prefs.edit()
                .putLong(KEY_CAMERA_DEVICE_ID, deviceId)
                .putInt(KEY_LAST_CAMERA_VERIFY_MODE, verifyMode)
                .putInt(KEY_LAST_CAMERA_VERIFY_DATA, verifyData)
                .apply()

            Log.i(
                TAG,
                "DJI camera connection state verifyMode=$verifyMode",
            )

            if (verifyMode == 2 && verifyData == 0) {
                protocolConnected = true
                reconnectEnabled = true
                reconnectAttempt = 0
                lastCameraRxAtMs = SystemClock.elapsedRealtime()
                prefs.edit()
                    .putBoolean(KEY_PROTOCOL_CONNECTED_ONCE, true)
                    .apply()
                cancelKeepAwake()
                keepAwakeFaulted = false
                rideEvent("LINK_READY", "intervalMs=$keepAwakeIntervalMs")
                logStatus("KNOWN_DEVICE: connected and ready")
                publishState(connection = CameraConnectionState.CONNECTED)
                mainHandler.removeCallbacks(livenessCheck)
                mainHandler.postDelayed(livenessCheck, LIVENESS_CHECK_MS)
                queueConnectionResponse(frame.seq)
                mainHandler.postDelayed({ queryVersion() }, 250)
                mainHandler.postDelayed({ subscribeCameraStatus() }, 500)
                startGpsStream()
                runPendingActionIfNeeded()
                scheduleKeepAwakeIfNeeded(
                    delayMs = keepAwakeIntervalMs,
                    reason = "post-connect",
                )
                mainHandler.postDelayed({ drainOperationQueue() }, 900)
            } else {
                if (!connectionRejectionHandled) {
                    connectionRejectionHandled = true
                    logStatus("DJI protocol rejected; approve on camera then retry")
                    mainHandler.postDelayed({ disconnect() }, 500)
                }
            }
            return
        }

        if (frame.payload.size >= 9) {
            val deviceId = DjiProtocol.readU32(frame.payload, 0)
            val retCode = frame.payload[4].toUByteInt()
            Log.i(TAG, "DJI connection request response retCode=$retCode")
            if (retCode == 0) {
                logStatus("DJI handshake accepted; waiting for camera approval")
            } else {
                logStatus("DJI handshake response retCode=$retCode")
            }
        }
    }

    private fun queueConnectionResponse(seq: Int) {
        queueFrame(
            label = "connection response",
            cmdSet = DjiProtocol.CMD_SET_COMMON,
            cmdId = DjiProtocol.CMD_ID_CONNECTION_REQUEST,
            cmdType = DjiProtocol.ACK_NO_RESPONSE,
            payload = DjiProtocol.connectionResponsePayload(0x12345678, cameraNumber = 0),
            seq = seq,
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
        )
    }

    private fun queryVersion() {
        queueFrame(
            label = "version query",
            cmdSet = DjiProtocol.CMD_SET_COMMON,
            cmdId = DjiProtocol.CMD_ID_VERSION_QUERY,
            cmdType = DjiProtocol.CMD_WAIT_RESULT,
            payload = DjiProtocol.versionQueryPayload(),
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
    }

    private fun subscribeCameraStatus() {
        queueFrame(
            label = "camera status subscription",
            cmdSet = DjiProtocol.CMD_SET_CAMERA,
            cmdId = DjiProtocol.CMD_ID_STATUS_SUBSCRIPTION,
            cmdType = DjiProtocol.CMD_NO_RESPONSE,
            payload = DjiProtocol.statusSubscriptionPayload(),
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
        )
    }

    private fun handleVersionFrame(frame: DjiProtocol.Frame) {
        if (frame.payload.size < 18) {
            Log.w(TAG, "DJI version payload too short length=${frame.payload.size}")
            return
        }

        val ack = DjiProtocol.readU16(frame.payload, 0)
        val productId = frame.payload.copyOfRange(2, 18).toAscii()
        val sdkVersion = frame.payload.copyOfRange(18, frame.payload.size).toAscii()
        prefs.edit()
            .putString(KEY_PRODUCT_ID, productId)
            .putString(KEY_SDK_VERSION, sdkVersion)
            .apply()

        Log.i(TAG, "DJI camera version ack=$ack productId=$productId sdkVersion=$sdkVersion")
    }

    private fun handleCameraStatus(frame: DjiProtocol.Frame) {
        val cameraStatus = DjiProtocol.parseCameraStatus(frame.payload)
        if (cameraStatus == null) {
            Log.w(TAG, "DJI camera status payload too short length=${frame.payload.size}")
            return
        }

        latestBatteryPercent = cameraStatus.batteryPercent
        latestCameraStatus = cameraStatus.status
        latestCameraMode = cameraStatus.mode
        latestVideoResolution = cameraStatus.videoResolution
        latestVideoFps = cameraStatus.fps
        val wasRecording = latestRecording
        latestRecording = cameraStatus.mode == CAMERA_MODE_VIDEO &&
            cameraStatus.status == CAMERA_STATUS_IN_PROGRESS

        if (wasRecording != latestRecording) rideEvent("RECORDING", "active=$latestRecording")
        if (latestRecording) {
            if (keepAwakeScheduled || keepAwakeAttempt != null) cancelKeepAwake()
        } else {
            scheduleKeepAwakeIfNeeded(
                delayMs = if (wasRecording) 0L else keepAwakeIntervalMs,
                reason = if (wasRecording) "recording stopped" else "idle status",
            )
        }

        if (photoState == PhotoCaptureState.REQUESTED &&
            cameraStatus.mode == CAMERA_MODE_PHOTO &&
            cameraStatus.status == CAMERA_STATUS_IN_PROGRESS
        ) {
            photoState = PhotoCaptureState.CAPTURING
        } else if (photoState == PhotoCaptureState.CAPTURING &&
            cameraStatus.status != CAMERA_STATUS_IN_PROGRESS
        ) {
            markPhotoComplete()
        }

        publishState(
            batteryPercent = cameraStatus.batteryPercent,
            displayOff = cameraStatus.status == CAMERA_STATUS_SCREEN_OFF,
            recording = latestRecording,
            recordingDurationSeconds = cameraStatus.recordTimeSeconds,
            remainingCapacityMb = cameraStatus.remainingCapacityMb,
            remainingRecordingSeconds = cameraStatus.remainingTimeSeconds,
            videoResolution = cameraStatus.videoResolution,
            videoFps = cameraStatus.fps,
            cameraMode = when (cameraStatus.mode) {
                CAMERA_MODE_VIDEO -> DjiCameraMode.VIDEO
                CAMERA_MODE_PHOTO -> DjiCameraMode.PHOTO
                else -> DjiCameraMode.OTHER
            },
        )

        val now = SystemClock.elapsedRealtime()
        val statusKey = "${cameraStatus.mode}:${cameraStatus.status}:${cameraStatus.powerMode}:${cameraStatus.batteryPercent}"
        if (statusKey == lastCameraStatusKey && now - lastCameraStatusLogAtMs < CAMERA_STATUS_LOG_INTERVAL_MS) {
            return
        }
        lastCameraStatusKey = statusKey
        lastCameraStatusLogAtMs = now

        Log.i(
            TAG,
            "DJI camera state mode=${DjiProtocol.modeName(cameraStatus.mode)}(0x%02X) "
                .format(cameraStatus.mode) +
                "status=${DjiProtocol.statusName(cameraStatus.status)}(0x%02X) ".format(cameraStatus.status) +
                "resolution=${cameraStatus.videoResolution} fps=${cameraStatus.fps} eis=${cameraStatus.eisMode} " +
                "recordTime=${cameraStatus.recordTimeSeconds}s remainingPhotos=${cameraStatus.remainingPhotos} " +
                "remainingTime=${cameraStatus.remainingTimeSeconds}s powerMode=${cameraStatus.powerMode} " +
                "battery=${cameraStatus.batteryPercent}%",
        )
    }

    private fun handleNewCameraStatus(frame: DjiProtocol.Frame) {
        val text = DjiProtocol.parseNewStatusText(frame.payload)
        if (text == null) {
            Log.w(TAG, "DJI new camera status payload too short length=${frame.payload.size}")
            return
        }

        Log.i(TAG, "DJI new camera state received")
    }

    private fun logReturnCode(label: String, frame: DjiProtocol.Frame) {
        val retCode = frame.payload.firstOrNull()?.toUByteInt()
        Log.i(TAG, "DJI $label response retCode=$retCode")
    }

    private fun queueFrame(
        label: String,
        cmdSet: Int,
        cmdId: Int,
        cmdType: Int,
        payload: ByteArray,
        writeType: Int,
        seq: Int = nextSeq(),
        keepAwakeToken: Long? = null,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val frame = DjiProtocol.createFrame(cmdSet, cmdId, cmdType, payload, seq)
        mainHandler.post {
            if (diagnosticConnectionPaused) return@post
            if (keepAwakeToken != null && !validKeepAwakeToken(keepAwakeToken)) return@post
            writeQueue.addLast(PendingWrite(label, frame, writeType, onComplete, keepAwakeToken))
            drainWriteQueue()
        }
    }

    private fun enqueueOperation(
        label: String,
        priority: CommandPriority,
        steps: List<CommandStep>,
        coalesceKey: String? = null,
        keepAwakeToken: Long? = null,
        onComplete: () -> Unit = {},
        cooldownLimitedHighlight: Boolean = false,
    ) {
        mainHandler.post {
            if (diagnosticConnectionPaused) return@post
            if (keepAwakeToken != null && !validKeepAwakeToken(keepAwakeToken)) return@post
            if (cooldownLimitedHighlight && !allowThresholdHighlight()) return@post
            if (coalesceKey != null) {
                operationQueue.removeAll { it.coalesceKey == coalesceKey }
            }
            operationOrder += 1
            operationQueue += CameraOperation(
                label = label,
                priority = priority,
                steps = steps,
                order = operationOrder,
                coalesceKey = coalesceKey,
                keepAwakeToken = keepAwakeToken,
                cooldownLimitedHighlight = cooldownLimitedHighlight,
                onComplete = onComplete,
            )
            Log.i(TAG, "DJI command queued label=$label priority=$priority depth=${operationQueue.size}")
            drainOperationQueue()
        }
    }

    private fun allowThresholdHighlight(): Boolean {
        val allowed = highlightCooldown.allows(
            SystemClock.elapsedRealtime(), DjiSettings.highlightCooldownSeconds(appContext),
        )
        if (!allowed) logStatus("DJI automatic highlight skipped: cooldown active")
        return allowed
    }

    private fun drainOperationQueue() {
        if (activeOperation != null || !isProtocolLinkFresh()) return
        val next = operationQueue
            .maxWithOrNull(compareBy<CameraOperation> { it.priority.weight }.thenBy { -it.order })
            ?: return
        operationQueue.remove(next)
        // Recheck after earlier queued highlights finish, including manual/event markers.
        if (next.cooldownLimitedHighlight && !allowThresholdHighlight()) {
            drainOperationQueue()
            return
        }
        activeOperation = ActiveOperation(next)
        Log.i(TAG, "DJI command started label=${next.label} priority=${next.priority}")
        executeActiveOperationStep()
    }

    private fun executeActiveOperationStep() {
        val active = activeOperation ?: return
        if (active.operation.coalesceKey == KEEP_AWAKE_OPERATION_KEY &&
            !validKeepAwakeToken(active.operation.keepAwakeToken)) {
            Log.i(TAG, "DJI keep awake command cancelled before execution: camera state changed")
            activeOperation = null
            drainOperationQueue()
            return
        }
        if (active.operation.coalesceKey in listOf(HIGHLIGHT_OPERATION_KEY, AUTO_HIGHLIGHT_OPERATION_KEY) && !latestRecording) {
            Log.i(TAG, "DJI highlight command cancelled before execution: recording stopped")
            activeOperation = null
            drainOperationQueue()
            return
        }
        val step = active.operation.steps.getOrNull(active.stepIndex)
        if (step == null) {
            Log.i(TAG, "DJI command completed label=${active.operation.label}")
            active.operation.onComplete()
            activeOperation = null
            drainOperationQueue()
            return
        }

        queueFrame(
            label = step.label,
            cmdSet = step.cmdSet,
            cmdId = step.cmdId,
            cmdType = step.cmdType,
            payload = step.payload,
            writeType = step.writeType,
            keepAwakeToken = active.operation.keepAwakeToken,
            onComplete = { success ->
                if (activeOperation !== active) return@queueFrame
                if (!success) {
                    Log.w(TAG, "DJI command step failed operation=${active.operation.label} step=${step.label}")
                    activeOperation = null
                    drainOperationQueue()
                    return@queueFrame
                }
                active.stepIndex += 1
                if (step.delayAfterMs > 0) {
                    mainHandler.postDelayed({ executeActiveOperationStep() }, step.delayAfterMs)
                } else {
                    executeActiveOperationStep()
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun drainWriteQueue() {
        if (writeInProgress) return
        val pending = writeQueue.removeFirstOrNull() ?: return
        if (pending.keepAwakeToken != null && !validKeepAwakeToken(pending.keepAwakeToken)) {
            pending.onComplete(false)
            drainWriteQueue()
            return
        }
        val gatt = gatt
        val characteristic = writeCharacteristic

        if (gatt == null || characteristic == null) {
            Log.w(TAG, "DJI TX skipped ${pending.label}: GATT/write characteristic missing")
            pending.onComplete(false)
            drainWriteQueue()
            return
        }

        if (pending.label == LABEL_GPS) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastGpsTxLogAtMs >= GPS_TX_LOG_INTERVAL_MS) {
                lastGpsTxLogAtMs = now
                Log.i(TAG, "DJI TX ${pending.label} sample 10Hz")
            }
        } else {
            Log.i(TAG, "DJI TX ${pending.label}")
        }
        writeInProgress = true
        activeWrite = pending

        pending.keepAwakeToken?.let { token ->
            val sequence = DjiProtocol.readU16(pending.frame, 8)
            keepAwakeAttempt?.sent(sequence, SystemClock.elapsedRealtime())
            rideEvent("QS_SENT", "cycle=$token seq=$sequence attempt=${keepAwakeAttempt?.count}")
            armKeepAwakeTimeout(token, queued = false)
        }
        val accepted = writeCharacteristic(gatt, characteristic, pending.frame, pending.writeType)
        if (!accepted) {
            Log.w(TAG, "DJI TX rejected by Android stack label=${pending.label}")
            completeActiveWrite(pending, false)
            return
        }

        if (pending.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            mainHandler.postDelayed({
                completeActiveWrite(pending, true)
            }, 80)
        }
    }

    private fun completeActiveWrite(pending: PendingWrite, success: Boolean) {
        if (activeWrite !== pending) return
        activeWrite = null
        writeInProgress = false
        if (pending.keepAwakeToken != null) rideEvent("QS_WRITE_RESULT", "cycle=${pending.keepAwakeToken} success=$success")
        pending.onComplete(success)
        drainWriteQueue()
    }

    private fun nextSeq(): Int {
        seq = (seq + 1) and 0xFFFF
        if (seq == 0) seq = 1
        return seq
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

    private fun saveCandidate(candidate: DjiCandidate) {
        prefs.edit()
            .putString(KEY_CAMERA_ADDRESS, candidate.device.address)
            .putString(KEY_CAMERA_NAME, candidate.name)
            .putInt(KEY_CAMERA_RSSI, candidate.rssi)
            .putString(KEY_LAST_ADVERTISEMENT, DjiProtocol.hex(candidate.advertisement))
            .apply()
    }

    private fun controllerId(): ByteArray {
        prefs.getString(KEY_CONTROLLER_ID, null)?.let { existing ->
            parseHex(existing)?.takeIf { it.size == 6 }?.let { return it }
        }

        val generated = ByteArray(6)
        Random.nextBytes(generated)
        generated[0] = (generated[0].toInt() or 0xC2).toByte()
        prefs.edit()
            .putString(KEY_CONTROLLER_ID, DjiProtocol.hex(generated))
            .apply()
        return generated
    }

    private fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun logServices(services: List<BluetoothGattService>) {
        services.forEach { service ->
            Log.i(TAG, "DJI discovered service=${service.uuid}")
            service.characteristics.forEach { characteristic ->
                Log.i(TAG, "DJI discovered characteristic=${characteristic.uuid}")
            }
        }
    }

    private fun keyReportStep(payload: ByteArray, label: String = "QS button short press"): CommandStep {
        return CommandStep(
            label = label,
            cmdSet = DjiProtocol.CMD_SET_COMMON,
            cmdId = DjiProtocol.CMD_ID_KEY_REPORT,
            cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
            payload = payload,
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
    }

    private fun recordControlStep(cameraCommandDeviceId: Long, start: Boolean): CommandStep {
        return CommandStep(
            label = if (start) "record control start" else "record control stop",
            cmdSet = DjiProtocol.CMD_SET_CAMERA,
            cmdId = DjiProtocol.CMD_ID_RECORD_CONTROL,
            cmdType = DjiProtocol.CMD_RESPONSE_OR_NOT,
            payload = DjiProtocol.recordControlPayload(cameraCommandDeviceId, start),
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
    }

    private fun shouldKeepAwake(): Boolean {
        return keepAwakeEnabled && protocolConnected && !latestRecording && !keepAwakeFaulted
    }

    private fun scheduleKeepAwakeIfNeeded(delayMs: Long = keepAwakeIntervalMs, reason: String) {
        if (!shouldKeepAwake()) {
            publishState()
            return
        }
        scheduleKeepAwakePulse(delayMs = delayMs, reason = reason)
    }

    private fun scheduleKeepAwakePulse(delayMs: Long, reason: String) {
        if (!shouldKeepAwake() || keepAwakeScheduled || keepAwakeAttempt != null) {
            publishState()
            return
        }
        keepAwakeScheduled = true
        mainHandler.postDelayed(keepAwakeQs, delayMs.coerceAtLeast(0))
        rideEvent("QS_SCHEDULED", "delayMs=$delayMs reason=$reason")
        Log.i(TAG, "DJI keep awake scheduled in ${delayMs}ms reason=$reason")
        publishState()
    }

    private fun validKeepAwakeToken(token: Long?): Boolean =
        token != null && token == keepAwakeGeneration && keepAwakeAttempt != null && shouldKeepAwake()

    private fun beginKeepAwakeAttempt() {
        keepAwakeGeneration++
        keepAwakeAttempt = KeepAwakeAttempt()
        sendKeepAwakeAttempt()
    }

    private fun sendKeepAwakeAttempt() {
        val token = keepAwakeGeneration
        if (!validKeepAwakeToken(token)) return
        keepAwakeQsCount++
        // Also bound time spent waiting behind other writes. Do not stack retries in a stalled queue.
        armKeepAwakeTimeout(token, queued = true)
        enqueueOperation(
            label = "keep awake QS short press #$keepAwakeQsCount",
            priority = CommandPriority.KEEP_AWAKE,
            coalesceKey = KEEP_AWAKE_OPERATION_KEY,
            keepAwakeToken = token,
            steps = listOf(keyReportStep(DjiProtocol.qsButtonShortPressPayload())),
        )
        logKeepAwakeMeasurement()
    }

    private fun armKeepAwakeTimeout(token: Long, queued: Boolean) {
        keepAwakeTimeout?.let(mainHandler::removeCallbacks)
        keepAwakeTimeout = Runnable {
            if (!validKeepAwakeToken(token)) return@Runnable
            when (keepAwakeAttempt?.timeout(queued, activeWrite?.keepAwakeToken == token, isProtocolLinkFresh())) {
                KeepAwakeAttempt.Timeout.WRITE_STALLED -> failKeepAwake("write stalled")
                KeepAwakeAttempt.Timeout.LINK_STALE -> failKeepAwake("link not fresh")
                KeepAwakeAttempt.Timeout.RETRY -> {
                    rideEvent("QS_RETRY", "cycle=$token reason=no_success_response")
                    sendKeepAwakeAttempt()
                }
                KeepAwakeAttempt.Timeout.EXHAUSTED -> failKeepAwake("no acknowledgement after three attempts")
                null -> Unit
            }
        }.also { mainHandler.postDelayed(it, KeepAwakeAttempt.ACK_TIMEOUT_MS) }
    }

    private fun handleKeepAwakeResponse(frame: DjiProtocol.Frame) {
        if (!frame.isResponse || !validKeepAwakeToken(keepAwakeGeneration)) return
        val attempt = keepAwakeAttempt ?: return
        if (attempt.contains(frame.seq)) {
            rideEvent("QS_RESPONSE", "cycle=$keepAwakeGeneration seq=${frame.seq} code=${frame.payload.singleOrNull()?.toInt()?.and(255)}")
        }
        when (val result = attempt.response(frame.seq, frame.payload)) {
            is KeepAwakeAttempt.Result.Success -> {
                val now = SystemClock.elapsedRealtime()
                rideEvent("QS_ACK", "cycle=$keepAwakeGeneration seq=${frame.seq} latencyMs=${now - result.sentAtMs}")
                cancelKeepAwake()
                scheduleKeepAwakeIfNeeded(
                    delayMs = (result.sentAtMs + keepAwakeIntervalMs - now).coerceAtLeast(0),
                    reason = "acknowledged QS",
                )
            }
            KeepAwakeAttempt.Result.InvalidCommand -> failKeepAwake("DJI rejected command format")
            KeepAwakeAttempt.Result.Ignore -> Unit // Timeout provides bounded recovery; no payload retained.
        }
    }

    private fun failKeepAwake(reason: String) {
        rideEvent("QS_UNCONFIRMED", "reason=$reason")
        keepAwakeFaulted = true
        cancelKeepAwake()
        logStatus("Keep awake could not be confirmed: $reason; toggle Keep awake to retry")
    }

    private fun cancelKeepAwake() {
        keepAwakeGeneration++
        keepAwakeAttempt = null
        keepAwakeScheduled = false
        keepAwakeTimeout?.let(mainHandler::removeCallbacks)
        keepAwakeTimeout = null
        mainHandler.removeCallbacks(keepAwakeQs)
        operationQueue.removeAll { it.coalesceKey == KEEP_AWAKE_OPERATION_KEY }
        writeQueue.removeAll { it.keepAwakeToken != null }
        val active = activeOperation
        if (active?.operation?.coalesceKey == KEEP_AWAKE_OPERATION_KEY && activeWrite?.keepAwakeToken == null) {
            activeOperation = null
            mainHandler.post { drainOperationQueue() }
        }
        // In-flight Android writes finish normally; they cannot be recalled safely.
        publishState()
    }

    private fun completePhotoWithoutStatus() {
        if (photoState == PhotoCaptureState.REQUESTED) {
            markPhotoComplete()
        }
    }

    private fun markPhotoComplete() {
        photoState = PhotoCaptureState.COMPLETE
        publishState()
        photoCompletedRunnable?.let(mainHandler::removeCallbacks)
        photoCompletedRunnable = Runnable {
            photoState = PhotoCaptureState.IDLE
            publishState()
        }.also { mainHandler.postDelayed(it, PHOTO_FEEDBACK_MS) }
    }

    private fun showHighlightFeedback(message: String) {
        highlightFeedback = message
        publishState()
        highlightFeedbackRunnable?.let(mainHandler::removeCallbacks)
        highlightFeedbackRunnable = Runnable {
            highlightFeedback = null
            publishState()
        }.also { mainHandler.postDelayed(it, BRIEF_FEEDBACK_MS) }
    }

    private fun publishState(
        connection: CameraConnectionState = cameraState.connection,
        batteryPercent: Int? = cameraState.batteryPercent,
        displayOff: Boolean = latestCameraStatus == CAMERA_STATUS_SCREEN_OFF,
        recording: Boolean = latestRecording,
        recordingDurationSeconds: Int = cameraState.recordingDurationSeconds,
        remainingCapacityMb: Long? = cameraState.remainingCapacityMb,
        remainingRecordingSeconds: Long? = cameraState.remainingRecordingSeconds,
        videoResolution: Int? = latestVideoResolution ?: cameraState.videoResolution,
        videoFps: Int? = latestVideoFps ?: cameraState.videoFps,
        cameraMode: DjiCameraMode = cameraState.cameraMode,
        lastMessage: String = cameraState.lastMessage,
    ) {
        val configured = hasKnownDevice()
        cameraState = cameraState.copy(
            diagnosticConnectionPaused = diagnosticConnectionPaused,
            configured = configured,
            connection = when {
                !configured -> CameraConnectionState.NOT_CONFIGURED
                sleepOfflineAssumed -> CameraConnectionState.DISCONNECTED
                else -> connection
            },
            batteryPercent = batteryPercent,
            keepAwakeEnabled = keepAwakeEnabled,
            keepAwakeActive = shouldKeepAwake(),
            keepAwakeIntervalSeconds = (keepAwakeIntervalMs / 1_000L).toInt(),
            displayOff = displayOff,
            recording = recording,
            recordingDurationSeconds = recordingDurationSeconds,
            remainingCapacityMb = remainingCapacityMb,
            remainingRecordingSeconds = remainingRecordingSeconds,
            videoResolution = videoResolution,
            videoFps = videoFps,
            cameraMode = cameraMode,
            highlightCount = highlightCount,
            highlightFeedback = highlightFeedback,
            photoState = photoState,
            lastMessage = lastMessage,
        )
        mainHandler.post { stateChanged(cameraState) }
    }

    private fun logStatus(message: String) {
        publishState(lastMessage = message)
        mainHandler.post { status(message) }
    }

    private fun logKeepAwakeMeasurement() {
        val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val battery = latestBatteryPercent?.let { "$it%" } ?: "unknown"
        Log.i(
            PREVENT_SLEEP_TAG,
            "timestamp=$timestamp battery=$battery qsCount=$keepAwakeQsCount",
        )
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun parseHex(value: String): ByteArray? {
        val cleaned = value.replace(":", " ").replace(",", " ")
            .split(" ")
            .filter { it.isNotBlank() }
        return runCatching {
            cleaned.map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }

    private fun Byte.toUByteInt(): Int = toInt() and 0xFF

    private fun ByteArray.toAscii(): String {
        return String(this, StandardCharsets.US_ASCII)
            .replace('\u0000', ' ')
            .trim()
    }

    private data class DjiCandidate(
        val device: BluetoothDevice,
        val name: String,
        val rssi: Int,
        val advertisement: ByteArray,
    )

    private data class PendingWrite(
        val label: String,
        val frame: ByteArray,
        val writeType: Int,
        val onComplete: (Boolean) -> Unit,
        val keepAwakeToken: Long? = null,
    )

    private data class CommandStep(
        val label: String,
        val cmdSet: Int,
        val cmdId: Int,
        val cmdType: Int,
        val payload: ByteArray,
        val writeType: Int,
        val delayAfterMs: Long = 0L,
    )

    private data class CameraOperation(
        val label: String,
        val priority: CommandPriority,
        val steps: List<CommandStep>,
        val order: Long,
        val coalesceKey: String?,
        val keepAwakeToken: Long?,
        val cooldownLimitedHighlight: Boolean,
        val onComplete: () -> Unit,
    )

    private data class ActiveOperation(
        val operation: CameraOperation,
        var stepIndex: Int = 0,
    )

    private enum class CommandPriority(val weight: Int) {
        TELEMETRY(10),
        KEEP_AWAKE(50),
        USER_ACTION(100),
    }

    private enum class PendingCameraAction(val label: String) {
        PHOTO("photo"),
        START_VIDEO("start video"),
        STOP_VIDEO("stop video"),
        SLEEP("sleep"),
        HIGHLIGHT("highlight"),
        TOGGLE_MODE("toggle mode"),
    }

    enum class ConnectionState(val label: String) {
        NOT_CONFIGURED("Not configured"),
        DISCONNECTED("Disconnected"),
        CONNECTED("Connected"),
    }

    companion object {
        private const val TAG = "KarooPoC"
        private const val SCAN_DURATION_MS = 5_000L
        private const val SAVED_SCAN_DURATION_MS = 5_000L
        private const val CONNECT_SCAN_OVERLAP_MS = 3_000L
        private const val LIVENESS_CHECK_MS = 2_000L
        private const val LIVENESS_TIMEOUT_MS = 8_000L
        private const val KEEP_AWAKE_LIVENESS_TIMEOUT_MS = 45_000L
        private const val CAMERA_STATUS_LOG_INTERVAL_MS = 5_000L
        private const val RECONNECT_AFTER_LOSS_MS = 1_000L
        private const val FAST_RECONNECT_DELAY_MS = 1_000L
        private const val SLOW_RECONNECT_DELAY_MS = 5_000L
        private const val FAST_RECONNECT_ATTEMPTS = 3
        private const val BLUETOOTH_RECOVERY_DELAY_MS = 2_000L
        private const val GPS_PUSH_INTERVAL_MS = 100L
        private const val GPS_TX_LOG_INTERVAL_MS = 5_000L
        private const val SLEEP_OFFLINE_DELAY_MS = 350L
        // Requires the recommended five-minute camera Auto Power Off setting.
        const val DEFAULT_KEEP_AWAKE_INTERVAL_SECONDS = 270
        const val MIN_KEEP_AWAKE_INTERVAL_SECONDS = 10
        const val MAX_KEEP_AWAKE_INTERVAL_SECONDS = 300
        private const val CAMERA_STATUS_SCREEN_OFF = 0x00
        private const val CAMERA_STATUS_IN_PROGRESS = 0x03
        private const val CAMERA_MODE_VIDEO = 0x01
        private const val CAMERA_MODE_PHOTO = 0x05
        private const val BRIEF_FEEDBACK_MS = 5_000L
        private const val PHOTO_FEEDBACK_MS = 1_500L
        private const val PREVENT_SLEEP_TAG = "KarooPreventSleep"
        private const val LABEL_GPS = "real GPS data push"
        private const val KEEP_AWAKE_OPERATION_KEY = "keep-awake"
        private const val AUTO_HIGHLIGHT_OPERATION_KEY = "automatic-recording-highlight"
        private const val HIGHLIGHT_OPERATION_KEY = "recording-highlight"
        private const val MODE_OPERATION_KEY = "mode-switch"

        private val UUID_SERVICE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_NOTIFY: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
        private val UUID_WRITE: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
        private val UUID_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val KEY_CAMERA_ADDRESS = "camera_address"
        private const val KEY_CAMERA_NAME = "camera_name"
        private const val KEY_CAMERA_RSSI = "camera_rssi"
        private const val KEY_LAST_ADVERTISEMENT = "last_advertisement"
        private const val KEY_CONTROLLER_ID = "controller_id"
        private const val KEY_LAST_VERIFY_DATA = "last_verify_data"
        private const val KEY_LAST_VERIFY_MODE = "last_verify_mode"
        private const val KEY_CAMERA_DEVICE_ID = "camera_device_id"
        private const val KEY_LAST_CAMERA_VERIFY_MODE = "last_camera_verify_mode"
        private const val KEY_LAST_CAMERA_VERIFY_DATA = "last_camera_verify_data"
        private const val KEY_PROTOCOL_CONNECTED_ONCE = "protocol_connected_once"
        private const val KEY_PRODUCT_ID = "product_id"
        private const val KEY_SDK_VERSION = "sdk_version"
    }
}

enum class CameraConnectionState {
    NOT_CONFIGURED,
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
}

enum class PhotoCaptureState {
    IDLE,
    REQUESTED,
    CAPTURING,
    COMPLETE,
}

enum class DjiCameraMode {
    UNKNOWN,
    VIDEO,
    PHOTO,
    OTHER,
}

data class DjiGpsTelemetry(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedNorthMetersPerSecond: Double,
    val speedEastMetersPerSecond: Double,
    val speedDownMetersPerSecond: Double,
    val verticalAccuracyMeters: Double,
    val horizontalAccuracyMeters: Double,
    val speedAccuracyMetersPerSecond: Double,
    val satelliteCount: Int,
)

data class DjiCameraState(
    val diagnosticConnectionPaused: Boolean = false,
    val configured: Boolean = false,
    val connection: CameraConnectionState = CameraConnectionState.NOT_CONFIGURED,
    val batteryPercent: Int? = null,
    val keepAwakeEnabled: Boolean = false,
    val keepAwakeActive: Boolean = false,
    val keepAwakeIntervalSeconds: Int = DjiOsmoBleClient.DEFAULT_KEEP_AWAKE_INTERVAL_SECONDS,
    val displayOff: Boolean = false,
    val recording: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val remainingCapacityMb: Long? = null,
    val remainingRecordingSeconds: Long? = null,
    val videoResolution: Int? = null,
    val videoFps: Int? = null,
    val cameraMode: DjiCameraMode = DjiCameraMode.UNKNOWN,
    val highlightCount: Int = 0,
    val highlightFeedback: String? = null,
    val photoState: PhotoCaptureState = PhotoCaptureState.IDLE,
    val lastMessage: String = "Starting DJI camera controller",
)
