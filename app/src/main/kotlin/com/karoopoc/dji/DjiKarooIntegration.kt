package com.karoopoc.dji

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.karoopoc.R
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.Lap
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlin.math.cos
import kotlin.math.sin

class DjiKarooIntegration(
    context: Context,
    private val karooSystem: KarooSystemService,
) {
    private val appContext = context.applicationContext
    private val notificationHandler = Handler(Looper.getMainLooper())
    private val notificationQueue = RideNotificationQueue<InRideAlert>(
        show = { alert, durationMs ->
            if (rideState is RideState.Recording) {
                karooSystem.dispatch(alert.copy(autoDismissMs = durationMs))
            }
        },
        schedule = { delayMs, action -> notificationHandler.postDelayed({ action() }, delayMs) },
        cancelScheduled = { notificationHandler.removeCallbacksAndMessages(null) },
    )
    private var cancelCameraObservation: (() -> Unit)? = null

    private var rideState: RideState = RideState.Idle
    private var latestCameraState = DjiCameraState()
    private val connectionNotification = CameraConnectionNotificationPolicy()
    private var latestLocation: OnLocationChanged? = null
    private var latestSpeedMetersPerSecond: Double? = null
    private var latestElevationMeters: Double? = null
    private var previousElevationMeters: Double? = null
    private var previousElevationAtMs: Long? = null
    private var verticalSpeedDownMetersPerSecond = 0.0
    private var latestDistanceMeters: Double? = null
    private var latestGradePercent: Double? = null
    private var climbs: List<OnNavigationState.NavigationState.Climb> = emptyList()
    private val completedClimbs = mutableSetOf<String>()

    private var powerWasAbove = false
    private var speedWasAbove = false
    private var hrWasInZoneFive = false
    private val downhillTrigger = GradientSpeedTrigger()
    private val uphillTrigger = GradientSpeedTrigger()
    private val rideMarkers = RideBoundaryMarkers()
    private var batteryAlertArmed = true
    private var storageAlertArmed = true
    private val autoStop = AutoStopPolicy()
    private val telemetryAt = mutableMapOf<String, Long>()
    private val automationHandler = Handler(Looper.getMainLooper())
    private var automationRunning = false
    private var stopped = false
    private val automationTick = object : Runnable {
        override fun run() {
            if (!automationRunning) return
            evaluateAutoStop()
            automationHandler.postDelayed(this, 1000)
        }
    }
    private var previousHighlightCount = 0

    fun start() {
        stopped = false
        automationRunning = true
        automationHandler.post(automationTick)
        cancelCameraObservation = DjiCameraController.observe(appContext, ::onCameraState)

        karooSystem.addConsumer<RideState>(onEvent = { next -> automationHandler.post { onRideState(next) } })
        karooSystem.addConsumer<Lap> { lap ->
            val settings = DjiSettings.read(appContext)
            if (settings.autoHighlightsEnabled && settings.lapHighlights) {
                requestHighlight(appContext.getString(R.string.highlight_lap_feedback, lap.number), cooldownExempt = true)
            }
        }
        karooSystem.addConsumer<UserProfile> { _ -> }
        karooSystem.addConsumer<OnLocationChanged> { location ->
            latestLocation = location
            publishGpsTelemetry()
        }
        karooSystem.addConsumer<OnNavigationState> { event ->
            climbs = when (val navigation = event.state) {
                is OnNavigationState.NavigationState.NavigatingRoute -> navigation.climbs
                is OnNavigationState.NavigationState.NavigatingToDestination -> navigation.climbs
                OnNavigationState.NavigationState.Idle -> emptyList()
            }
            checkClimbSummits()
        }

        consumeStream(DataType.Type.POWER, DataType.Field.POWER, ::onPower)
        consumeStream(DataType.Type.SPEED, DataType.Field.SPEED, ::onSpeed)
        consumeStream(DataType.Type.HEART_RATE, DataType.Field.HEART_RATE, ::onHeartRate)
        consumeStream(DataType.Type.ELEVATION_GRADE, DataType.Field.ELEVATION_GRADE, ::onGrade)
        consumeStream(DataType.Type.DISTANCE, DataType.Field.DISTANCE, ::onDistance)
        consumeStream(
            DataType.Type.PRESSURE_ELEVATION_CORRECTION,
            DataType.Field.PRESSURE_ELEVATION,
            ::onElevation,
        )
    }

    fun stop() {
        stopped = true
        automationRunning = false
        automationHandler.removeCallbacksAndMessages(null)
        DjiCameraController.cancelAutomation(appContext)
        cancelCameraObservation?.invoke()
        cancelCameraObservation = null
        notificationHandler.post { notificationQueue.clear() }
    }

    private fun consumeStream(typeId: String, fieldId: String, onValue: (Double) -> Unit) {
        karooSystem.addConsumer<OnStreamState>(OnStreamState.StartStreaming(typeId)) { event ->
            val streaming = event.state as? StreamState.Streaming
            if (streaming == null) {
                automationHandler.post { telemetryAt.remove(typeId) }
                return@addConsumer
            }
            val value = streaming.dataPoint.values[fieldId] ?: streaming.dataPoint.singleValue ?: return@addConsumer
            automationHandler.post {
                if (value.isFinite()) {
                    telemetryAt[typeId] = SystemClock.elapsedRealtime()
                    onValue(value)
                } else telemetryAt.remove(typeId)
            }
        }
    }

    private fun onRideState(next: RideState) {
        val wasRiding = rideState is RideState.Recording || rideState is RideState.Paused
        val isRiding = next is RideState.Recording || next is RideState.Paused
        val settings = DjiSettings.read(appContext)
        val marker = rideMarkers.onRideState(
            next,
            cameraRecording = latestCameraState.recording && latestCameraState.connection == CameraConnectionState.CONNECTED,
            enabled = settings.autoHighlightsEnabled && settings.rideBookmarks &&
                (settings.mode == AutomationMode.HIGHLIGHTS || settings.mode == AutomationMode.RECORDING && settings.addAutomaticHighlights),
        )
        if (marker != null) {
            val reason = appContext.getString(if (marker == RideBoundaryMarkers.Marker.START) {
                R.string.highlight_ride_start_feedback
            } else {
                R.string.highlight_ride_end_feedback
            })
            Log.i(TAG, "Automatic highlight requested reason=$reason")
            // Boundary markers never auto-start a recording and always bypass threshold cooldown.
            DjiCameraController.addHighlight(appContext, reason, cooldownLimited = false)
        }
        rideState = next
        if (next !is RideState.Recording) {
            DjiCameraController.cancelAutomation(appContext)
            autoStop.event()
            notificationHandler.post { notificationQueue.clearPending() }
        }
        DjiCameraController.setRideActive(appContext, isRiding, next is RideState.Recording)

        if (!wasRiding && next is RideState.Recording) {
            batteryAlertArmed = true
            storageAlertArmed = true
            completedClimbs.clear()
            resetThresholdEdges()
        }

        if (!isRiding) {
            completedClimbs.clear()
            resetThresholdEdges()
        }
    }

    private fun onCameraState(state: DjiCameraState) {
        latestCameraState = state
        if (connectionNotification.onConnectionState(state.connection == CameraConnectionState.CONNECTED)) {
            showAlert(
                "dji-camera-connected",
                appContext.getString(R.string.notification_connected),
                null,
                R.color.dji_confirm,
            )
        }
        val settings = DjiSettings.read(appContext)

        if (state.highlightCount > previousHighlightCount) {
            showAlert("dji-highlight-${state.highlightCount}", appContext.getString(R.string.alert_highlight_added), null, R.color.dji_confirm)
        }
        previousHighlightCount = state.highlightCount

        checkThresholdAlerts(state)
    }

    private fun onPower(watts: Double) {
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled) {
            powerWasAbove = false
            return
        }
        val threshold = settings.peakPowerWatts.toDouble()
        if (settings.peakPowerHighlights && !powerWasAbove && watts >= threshold) {
            requestHighlight(appContext.getString(R.string.highlight_peak_power_feedback, watts.toInt()))
        }
        powerWasAbove = if (powerWasAbove) ThresholdPolicy.remainsAbove(watts, threshold, 50.0) else watts >= threshold
    }

    private fun onSpeed(metersPerSecond: Double) {
        latestSpeedMetersPerSecond = metersPerSecond
        val kph = metersPerSecond * 3.6
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled) {
            speedWasAbove = false
            checkGradientTriggers()
            publishGpsTelemetry()
            return
        }
        val threshold = settings.maxSpeedKph.toDouble()
        if (settings.maxSpeedHighlights && !speedWasAbove && kph >= threshold) {
            requestHighlight(appContext.getString(R.string.highlight_max_speed_feedback, kph.toInt()))
        }
        speedWasAbove = if (speedWasAbove) ThresholdPolicy.remainsAbove(kph, threshold, 3.0) else kph >= threshold
        checkGradientTriggers()
        publishGpsTelemetry()
    }

    private fun onHeartRate(heartRate: Double) {
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled) {
            hrWasInZoneFive = false
            return
        }
        val threshold = settings.peakHeartRateBpm.toDouble()
        val inZoneFive = heartRate >= threshold
        if (settings.peakHeartRateHighlights && !hrWasInZoneFive && inZoneFive) {
            requestHighlight(appContext.getString(R.string.highlight_peak_hr_feedback, heartRate.toInt()))
        }
        hrWasInZoneFive = if (hrWasInZoneFive) ThresholdPolicy.remainsAbove(heartRate, threshold, 3.0) else inZoneFive
    }

    private fun onGrade(grade: Double) {
        latestGradePercent = grade
        checkGradientTriggers()
    }

    private fun checkGradientTriggers() {
        val settings = DjiSettings.read(appContext)
        val speedKph = latestSpeedMetersPerSecond?.times(3.6)
        if (downhillTrigger.update(speedKph, latestGradePercent,
                settings.autoHighlightsEnabled && settings.highSpeedDescentHighlights,
                settings.descentSpeedKph, settings.descentGradePercent, settings.descentRequireSpeed)) {
            requestHighlight(appContext.getString(R.string.highlight_descent_feedback))
        }
        if (uphillTrigger.update(speedKph, latestGradePercent,
                settings.autoHighlightsEnabled && settings.uphillHighlights,
                settings.uphillSpeedKph, settings.uphillGradePercent, settings.uphillRequireSpeed)) {
            requestHighlight(appContext.getString(R.string.highlight_uphill_feedback))
        }
    }

    private fun onDistance(distanceMeters: Double) {
        latestDistanceMeters = distanceMeters
        checkClimbSummits()
    }

    private fun checkClimbSummits() {
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled || !settings.climbSummitHighlights) return
        val distance = latestDistanceMeters ?: return
        climbs.forEach { climb ->
            val summit = climb.startDistance + climb.length
            val key = "${climb.startDistance.toInt()}:${climb.length.toInt()}:${climb.grade}"
            if (distance >= summit && completedClimbs.add(key)) {
                requestHighlight(appContext.getString(R.string.highlight_climb_feedback), cooldownExempt = true)
            }
        }
    }

    private fun onElevation(elevationMeters: Double) {
        val now = SystemClock.elapsedRealtime()
        val previous = previousElevationMeters
        val previousAt = previousElevationAtMs
        if (previous != null && previousAt != null && now > previousAt) {
            val seconds = (now - previousAt) / 1_000.0
            verticalSpeedDownMetersPerSecond = -((elevationMeters - previous) / seconds)
        }
        previousElevationMeters = elevationMeters
        previousElevationAtMs = now
        latestElevationMeters = elevationMeters
        publishGpsTelemetry()
    }

    private fun publishGpsTelemetry() {
        val location = latestLocation ?: return
        val speed = latestSpeedMetersPerSecond ?: 0.0
        val heading = location.orientation
        val radians = Math.toRadians(heading ?: 0.0)
        DjiCameraController.updateGpsTelemetry(
            appContext,
            DjiGpsTelemetry(
                latitude = location.lat,
                longitude = location.lng,
                altitudeMeters = latestElevationMeters ?: 0.0,
                speedNorthMetersPerSecond = if (heading == null) 0.0 else speed * cos(radians),
                speedEastMetersPerSecond = if (heading == null) 0.0 else speed * sin(radians),
                speedDownMetersPerSecond = verticalSpeedDownMetersPerSecond,
                horizontalAccuracyMeters = 10.0,
                verticalAccuracyMeters = 10.0,
                speedAccuracyMetersPerSecond = 1.0,
                satelliteCount = 10,
            ),
        )
    }

    private fun requestHighlight(reason: String, cooldownExempt: Boolean = false) {
        // Event callbacks and telemetry share the same serialized decision path.
        automationHandler.post {
            val settings = DjiSettings.read(appContext)
            val state = DjiCameraController.state()
            if (stopped || !settings.autoHighlightsEnabled || rideState !is RideState.Recording ||
                state.connection != CameraConnectionState.CONNECTED) return@post
            if (state.automaticRecordingId != 0L) DjiCameraController.holdAutomaticRecording(appContext)
            autoStop.event() // Marker cooldown never suppresses the recording extension.
            if (settings.mode == AutomationMode.PHOTOS) {
                if (!state.recording) DjiCameraController.automaticPhoto(appContext)
                return@post
            }
            if (!state.recording) {
                if (settings.mode == AutomationMode.RECORDING) DjiCameraController.automaticStart(appContext)
            } else if (settings.mode == AutomationMode.HIGHLIGHTS || settings.addAutomaticHighlights) {
                DjiCameraController.addHighlight(appContext, reason, cooldownLimited = !cooldownExempt)
            }
        }
    }

    private fun evaluateAutoStop() {
        val settings = DjiSettings.read(appContext)
        val state = DjiCameraController.state()
        val enabled = settings.autoHighlightsEnabled && settings.mode == AutomationMode.RECORDING &&
            settings.autoStop && rideState is RideState.Recording && state.recording &&
            state.connection == CameraConnectionState.CONNECTED
        val now = SystemClock.elapsedRealtime()
        val active = activeThresholds(settings, now)
        if (state.automaticRecordingId != 0L && active != false) DjiCameraController.holdAutomaticRecording(appContext)
        if (autoStop.update(now, state.automaticRecordingId, enabled, active, settings.stopAfterSeconds)) {
            DjiCameraController.automaticStop(appContext, state.automaticRecordingId)
        }
    }

    private fun activeThresholds(s: DjiAutomationSettings, now: Long): Boolean? {
        fun fresh(type: String) = telemetryAt[type]?.let { now - it <= 10000 } == true
        val states = mutableListOf<Boolean?>()
        if (s.peakPowerHighlights) states += powerWasAbove.takeIf { fresh(DataType.Type.POWER) }
        if (s.maxSpeedHighlights) states += speedWasAbove.takeIf { fresh(DataType.Type.SPEED) }
        if (s.peakHeartRateHighlights) states += hrWasInZoneFive.takeIf { fresh(DataType.Type.HEART_RATE) }
        if (s.highSpeedDescentHighlights) states += downhillTrigger.active.takeIf {
            fresh(DataType.Type.ELEVATION_GRADE) && (!s.descentRequireSpeed || fresh(DataType.Type.SPEED))
        }
        if (s.uphillHighlights) states += uphillTrigger.active.takeIf {
            fresh(DataType.Type.ELEVATION_GRADE) && (!s.uphillRequireSpeed || fresh(DataType.Type.SPEED))
        }
        return when { states.any { it == true } -> true; states.any { it == null } -> null; else -> false }
    }

    private fun checkThresholdAlerts(state: DjiCameraState) {
        if (rideState !is RideState.Recording) return
        val settings = DjiSettings.read(appContext)
        val battery = state.batteryPercent
        if (settings.lowBatteryAlert && battery != null && battery <= DjiSettings.LOW_BATTERY_PERCENT) {
            if (batteryAlertArmed) {
                batteryAlertArmed = false
                showAlert(
                    "dji-low-battery",
                    appContext.getString(R.string.alert_osmo_battery_low),
                    appContext.getString(R.string.alert_battery_remaining, battery),
                    R.color.dji_warning,
                )
            }
        } else if (battery == null || battery > DjiSettings.LOW_BATTERY_PERCENT + 2) {
            batteryAlertArmed = true
        }

        val storage = state.remainingCapacityMb
        if (settings.lowStorageAlert && storage != null && storage in 0L..DjiSettings.LOW_STORAGE_MB) {
            if (storageAlertArmed) {
                storageAlertArmed = false
                showAlert("dji-low-storage", appContext.getString(R.string.alert_osmo_storage_low), null, R.color.dji_warning)
            }
        } else if (storage == null || storage > DjiSettings.LOW_STORAGE_MB + 512L) {
            storageAlertArmed = true
        }
    }

    private fun showAlert(id: String, title: String, detail: String?, backgroundColor: Int) {
        if (rideState !is RideState.Recording) return
        notificationHandler.post {
            // The ride may have paused or ended while this work waited for the main thread.
            if (rideState !is RideState.Recording) return@post
            notificationQueue.enqueue(
                InRideAlert(
                    id = id,
                    icon = R.drawable.ic_launcher_foreground,
                    title = title,
                    detail = detail,
                    autoDismissMs = NotificationDuration.MINIMUM_MS,
                    backgroundColor = backgroundColor,
                    textColor = R.color.white,
                ),
            )
        }
    }

    private fun resetThresholdEdges() {
        telemetryAt.clear()
        powerWasAbove = false
        speedWasAbove = false
        hrWasInZoneFive = false
        downhillTrigger.reset()
        uphillTrigger.reset()
    }

    companion object {
        private const val TAG = "KarooPoC"
    }
}
