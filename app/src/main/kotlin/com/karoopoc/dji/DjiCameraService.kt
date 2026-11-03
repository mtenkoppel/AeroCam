package com.karoopoc.dji

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.karoopoc.MainActivity
import com.karoopoc.R
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.RequestBluetooth
import java.util.concurrent.CopyOnWriteArraySet

object DjiCameraController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(DjiCameraState) -> Unit>()

    @Volatile
    private var currentState = DjiCameraState()
    private var serviceRequested = false

    fun state(): DjiCameraState = currentState

    fun observe(context: Context, listener: (DjiCameraState) -> Unit): () -> Unit {
        initializeSettings(context)
        ensureStarted(context)
        listeners.add(listener)
        listener(currentState)
        return { listeners.remove(listener) }
    }

    fun ensureStarted(context: Context) {
        if (serviceRequested) return
        serviceRequested = true
        dispatch(context, DjiCameraService.ACTION_START)
    }

    fun discover(context: Context) = dispatch(context, DjiCameraService.ACTION_DISCOVER)

    fun connect(context: Context) = dispatch(context, DjiCameraService.ACTION_CONNECT)

    fun diagnosticDisconnect(context: Context) = dispatch(context, DjiCameraService.ACTION_DIAGNOSTIC_DISCONNECT)

    fun diagnosticReconnect(context: Context) = dispatch(context, DjiCameraService.ACTION_DIAGNOSTIC_RECONNECT)

    fun takePhoto(context: Context) = dispatch(context, DjiCameraService.ACTION_PHOTO)

    fun startRecording(context: Context) = dispatch(context, DjiCameraService.ACTION_RECORD_START)

    fun toggleRecording(context: Context) = dispatch(context, DjiCameraService.ACTION_RECORD_TOGGLE)

    fun addHighlight(context: Context, feedback: String? = null) = dispatch(
        context,
        DjiCameraService.ACTION_HIGHLIGHT,
        extras = { feedback?.let { putExtra(DjiCameraService.EXTRA_HIGHLIGHT_FEEDBACK, it) } },
    )

    fun toggleMode(context: Context) = dispatch(context, DjiCameraService.ACTION_MODE_TOGGLE)

    fun sleep(context: Context) {
        preferences(context).edit().putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, false).apply()
        publish(
            currentState.copy(
                connection = CameraConnectionState.DISCONNECTED,
                keepAwakeEnabled = false,
                keepAwakeActive = false,
                recording = false,
                recordingDurationSeconds = 0,
            ),
        )
        dispatch(context, DjiCameraService.ACTION_SLEEP)
    }

    fun forget(context: Context) = dispatch(context, DjiCameraService.ACTION_FORGET)

    fun setKeepAwake(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, enabled).apply()
        publish(
            currentState.copy(
                keepAwakeEnabled = enabled,
                keepAwakeActive = if (enabled) currentState.keepAwakeActive else false,
            ),
        )
        dispatch(context, DjiCameraService.ACTION_KEEP_AWAKE_CHANGED)
    }

    fun setRideActive(context: Context, active: Boolean) {
        dispatch(
            context,
            DjiCameraService.ACTION_RIDE_STATE_CHANGED,
            extras = { putExtra(DjiCameraService.EXTRA_RIDE_ACTIVE, active) },
        )
    }

    fun updateGpsTelemetry(context: Context, telemetry: DjiGpsTelemetry) {
        dispatch(
            context,
            DjiCameraService.ACTION_GPS_UPDATED,
            extras = {
                putExtra(DjiCameraService.EXTRA_GPS_LATITUDE, telemetry.latitude)
                putExtra(DjiCameraService.EXTRA_GPS_LONGITUDE, telemetry.longitude)
                putExtra(DjiCameraService.EXTRA_GPS_ALTITUDE, telemetry.altitudeMeters)
                putExtra(DjiCameraService.EXTRA_GPS_SPEED_NORTH, telemetry.speedNorthMetersPerSecond)
                putExtra(DjiCameraService.EXTRA_GPS_SPEED_EAST, telemetry.speedEastMetersPerSecond)
                putExtra(DjiCameraService.EXTRA_GPS_SPEED_DOWN, telemetry.speedDownMetersPerSecond)
                putExtra(DjiCameraService.EXTRA_GPS_VERTICAL_ACCURACY, telemetry.verticalAccuracyMeters)
                putExtra(DjiCameraService.EXTRA_GPS_HORIZONTAL_ACCURACY, telemetry.horizontalAccuracyMeters)
                putExtra(DjiCameraService.EXTRA_GPS_SPEED_ACCURACY, telemetry.speedAccuracyMetersPerSecond)
                putExtra(DjiCameraService.EXTRA_GPS_SATELLITES, telemetry.satelliteCount)
            },
        )
    }

    fun setKeepAwakeInterval(context: Context, seconds: Int) {
        val value = seconds.coerceIn(
            DjiOsmoBleClient.MIN_KEEP_AWAKE_INTERVAL_SECONDS,
            DjiOsmoBleClient.MAX_KEEP_AWAKE_INTERVAL_SECONDS,
        )
        preferences(context).edit().putInt(DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS, value).apply()
        publish(currentState.copy(keepAwakeIntervalSeconds = value))
        dispatch(context, DjiCameraService.ACTION_KEEP_AWAKE_CHANGED)
    }

    internal fun keepAwakeEnabled(context: Context): Boolean = preferences(context).getBoolean(
        DjiSettings.KEY_KEEP_AWAKE_ENABLED,
        false,
    )

    internal fun keepAwakeIntervalSeconds(context: Context): Int {
        return preferences(context).getInt(
            DjiSettings.KEY_KEEP_AWAKE_INTERVAL_SECONDS,
            DjiOsmoBleClient.DEFAULT_KEEP_AWAKE_INTERVAL_SECONDS,
        ).coerceIn(
            DjiOsmoBleClient.MIN_KEEP_AWAKE_INTERVAL_SECONDS,
            DjiOsmoBleClient.MAX_KEEP_AWAKE_INTERVAL_SECONDS,
        )
    }

    internal fun publish(state: DjiCameraState) {
        currentState = state
        mainHandler.post { listeners.forEach { it(state) } }
    }

    internal fun serviceStopped() {
        serviceRequested = false
        publish(currentState.copy(connection = CameraConnectionState.DISCONNECTED))
    }

    private fun initializeSettings(context: Context) {
        publish(
            currentState.copy(
                keepAwakeEnabled = keepAwakeEnabled(context),
                keepAwakeIntervalSeconds = keepAwakeIntervalSeconds(context),
            ),
        )
    }

    private fun dispatch(context: Context, action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context.applicationContext, DjiCameraService::class.java)
            .setAction(action)
            .apply(extras)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.applicationContext.startForegroundService(intent)
            } else {
                context.applicationContext.startService(intent)
            }
        }.onFailure {
            serviceRequested = false
            Log.e(TAG, "Unable to dispatch DJI camera action=$action", it)
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(DjiSettings.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val TAG = "KarooPoC"
}

class DjiCameraService : Service() {
    private lateinit var client: DjiOsmoBleClient
    private lateinit var karooSystem: KarooSystemService
    private var lastNotificationText: String? = null
    private var lastBluetoothRequestAtMs = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        client = DjiOsmoBleClient(
            context = applicationContext,
            status = { message -> Log.i(TAG, "DJI camera service: $message") },
            requestBluetooth = { reason -> requestBluetooth(reason) },
            stateChanged = { state ->
                DjiCameraController.publish(state)
                updateNotification(state)
            },
        )
        karooSystem = KarooSystemService(this)
        karooSystem.connect { connected ->
            Log.i(TAG, "DJI camera KarooSystem connected=$connected")
            if (connected) {
                requestBluetooth("Karoo System connected", force = true)
                if (client.hasKnownDevice()) client.autoConnectKnownDevice()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification(DjiCameraController.state()))
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> {
                applyKeepAwakeSettings()
                if (client.hasKnownDevice()) client.autoConnectKnownDevice()
            }
            ACTION_DISCOVER -> client.startDiscoveryAndHandshake()
            ACTION_CONNECT -> client.autoConnectKnownDevice()
            ACTION_DIAGNOSTIC_DISCONNECT -> client.diagnosticDisconnect()
            ACTION_DIAGNOSTIC_RECONNECT -> client.diagnosticReconnect()
            ACTION_PHOTO -> client.triggerPhoto()
            ACTION_RECORD_START -> {
                if (!client.state().recording) client.startVideoRecording()
            }
            ACTION_RECORD_TOGGLE -> {
                if (client.state().recording) client.stopVideoRecording() else client.startVideoRecording()
            }
            ACTION_HIGHLIGHT -> client.addHighlight(intent?.getStringExtra(EXTRA_HIGHLIGHT_FEEDBACK))
            ACTION_MODE_TOGGLE -> client.toggleMode()
            ACTION_SLEEP -> {
                client.setKeepAwake(false, DjiCameraController.keepAwakeIntervalSeconds(this))
                client.sleepCamera()
            }
            ACTION_FORGET -> client.forgetKnownDevice()
            ACTION_KEEP_AWAKE_CHANGED -> applyKeepAwakeSettings()
            ACTION_RIDE_STATE_CHANGED -> client.setRideActive(
                intent?.getBooleanExtra(EXTRA_RIDE_ACTIVE, false) ?: false,
            )
            ACTION_GPS_UPDATED -> client.updateGpsTelemetry(
                DjiGpsTelemetry(
                    latitude = intent?.getDoubleExtra(EXTRA_GPS_LATITUDE, 0.0) ?: 0.0,
                    longitude = intent?.getDoubleExtra(EXTRA_GPS_LONGITUDE, 0.0) ?: 0.0,
                    altitudeMeters = intent?.getDoubleExtra(EXTRA_GPS_ALTITUDE, 0.0) ?: 0.0,
                    speedNorthMetersPerSecond = intent?.getDoubleExtra(EXTRA_GPS_SPEED_NORTH, 0.0) ?: 0.0,
                    speedEastMetersPerSecond = intent?.getDoubleExtra(EXTRA_GPS_SPEED_EAST, 0.0) ?: 0.0,
                    speedDownMetersPerSecond = intent?.getDoubleExtra(EXTRA_GPS_SPEED_DOWN, 0.0) ?: 0.0,
                    verticalAccuracyMeters = intent?.getDoubleExtra(EXTRA_GPS_VERTICAL_ACCURACY, 10.0) ?: 10.0,
                    horizontalAccuracyMeters = intent?.getDoubleExtra(EXTRA_GPS_HORIZONTAL_ACCURACY, 10.0) ?: 10.0,
                    speedAccuracyMetersPerSecond = intent?.getDoubleExtra(EXTRA_GPS_SPEED_ACCURACY, 1.0) ?: 1.0,
                    satelliteCount = intent?.getIntExtra(EXTRA_GPS_SATELLITES, 10) ?: 10,
                ),
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client.disconnect()
        if (karooSystem.connected) {
            val dispatched = karooSystem.dispatch(ReleaseBluetooth(BLUETOOTH_RESOURCE))
            Log.i(TAG, "Karoo ReleaseBluetooth dispatched=$dispatched resource=$BLUETOOTH_RESOURCE")
        }
        karooSystem.disconnect()
        DjiCameraController.serviceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyKeepAwakeSettings() {
        client.setKeepAwake(
            enabled = DjiCameraController.keepAwakeEnabled(this),
            intervalSeconds = DjiCameraController.keepAwakeIntervalSeconds(this),
        )
    }

    private fun requestBluetooth(reason: String, force: Boolean = false) {
        if (!karooSystem.connected) {
            Log.i(TAG, "Karoo RequestBluetooth deferred reason=$reason; Karoo System disconnected")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastBluetoothRequestAtMs < BLUETOOTH_REQUEST_DEBOUNCE_MS) return
        lastBluetoothRequestAtMs = now
        val dispatched = karooSystem.dispatch(RequestBluetooth(BLUETOOTH_RESOURCE))
        Log.i(
            TAG,
            "Karoo RequestBluetooth dispatched=$dispatched resource=$BLUETOOTH_RESOURCE reason=$reason",
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.dji_camera_service),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(state: DjiCameraState) {
        val text = notificationText(state)
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(state))
    }

    private fun createNotification(state: DjiCameraState): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText(state))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(true)
            .build()
    }

    private fun notificationText(state: DjiCameraState): String {
        return when {
            state.recording -> getString(R.string.notification_recording, formatDuration(state.recordingDurationSeconds))
            state.connection == CameraConnectionState.CONNECTED && state.keepAwakeActive ->
                getString(R.string.notification_keep_awake)
            state.connection == CameraConnectionState.CONNECTED -> getString(R.string.notification_connected)
            state.connection == CameraConnectionState.SEARCHING ||
                state.connection == CameraConnectionState.CONNECTING -> getString(R.string.notification_connecting)
            else -> getString(R.string.notification_disconnected)
        }
    }

    private fun formatDuration(seconds: Int): String {
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    companion object {
        private const val TAG = "KarooPoC"
        private const val NOTIFICATION_CHANNEL_ID = "dji-camera"
        private const val NOTIFICATION_ID = 4104
        private const val BLUETOOTH_RESOURCE = "dji-camera"
        private const val BLUETOOTH_REQUEST_DEBOUNCE_MS = 2_000L

        const val ACTION_START = "com.karoopoc.dji.START"
        const val ACTION_DISCOVER = "com.karoopoc.dji.DISCOVER"
        const val ACTION_DIAGNOSTIC_DISCONNECT = "com.karoopoc.dji.DIAGNOSTIC_DISCONNECT"
        const val ACTION_DIAGNOSTIC_RECONNECT = "com.karoopoc.dji.DIAGNOSTIC_RECONNECT"
        const val ACTION_CONNECT = "com.karoopoc.dji.CONNECT"
        const val ACTION_PHOTO = "com.karoopoc.dji.PHOTO"
        const val ACTION_RECORD_START = "com.karoopoc.dji.RECORD_START"
        const val ACTION_RECORD_TOGGLE = "com.karoopoc.dji.RECORD_TOGGLE"
        const val ACTION_HIGHLIGHT = "com.karoopoc.dji.HIGHLIGHT"
        const val ACTION_MODE_TOGGLE = "com.karoopoc.dji.MODE_TOGGLE"
        const val ACTION_SLEEP = "com.karoopoc.dji.SLEEP"
        const val ACTION_FORGET = "com.karoopoc.dji.FORGET"
        const val ACTION_KEEP_AWAKE_CHANGED = "com.karoopoc.dji.KEEP_AWAKE_CHANGED"
        const val ACTION_RIDE_STATE_CHANGED = "com.karoopoc.dji.RIDE_STATE_CHANGED"
        const val ACTION_GPS_UPDATED = "com.karoopoc.dji.GPS_UPDATED"

        const val EXTRA_RIDE_ACTIVE = "ride_active"
        const val EXTRA_HIGHLIGHT_FEEDBACK = "highlight_feedback"
        const val EXTRA_GPS_LATITUDE = "gps_latitude"
        const val EXTRA_GPS_LONGITUDE = "gps_longitude"
        const val EXTRA_GPS_ALTITUDE = "gps_altitude"
        const val EXTRA_GPS_SPEED_NORTH = "gps_speed_north"
        const val EXTRA_GPS_SPEED_EAST = "gps_speed_east"
        const val EXTRA_GPS_SPEED_DOWN = "gps_speed_down"
        const val EXTRA_GPS_VERTICAL_ACCURACY = "gps_vertical_accuracy"
        const val EXTRA_GPS_HORIZONTAL_ACCURACY = "gps_horizontal_accuracy"
        const val EXTRA_GPS_SPEED_ACCURACY = "gps_speed_accuracy"
        const val EXTRA_GPS_SATELLITES = "gps_satellites"
    }
}
