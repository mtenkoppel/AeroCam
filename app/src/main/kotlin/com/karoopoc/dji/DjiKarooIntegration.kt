package com.karoopoc.dji

import android.content.Context
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
    private var cancelCameraObservation: (() -> Unit)? = null

    private var rideState: RideState = RideState.Idle
    private var latestCameraState = DjiCameraState()
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
    private var descentWasActive = false
    private var startBookmarkSent = false
    private var batteryAlertArmed = true
    private var storageAlertArmed = true
    private var autoStartRecordingPending = false
    private var previousRecording = false
    private var previousPhotoState = PhotoCaptureState.IDLE
    private var previousHighlightCount = 0

    fun start() {
        cancelCameraObservation = DjiCameraController.observe(appContext, ::onCameraState)

        karooSystem.addConsumer<RideState>(onEvent = ::onRideState)
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
        cancelCameraObservation?.invoke()
        cancelCameraObservation = null
    }

    private fun consumeStream(typeId: String, fieldId: String, onValue: (Double) -> Unit) {
        karooSystem.addConsumer<OnStreamState>(OnStreamState.StartStreaming(typeId)) { event ->
            val streaming = event.state as? StreamState.Streaming ?: return@addConsumer
            val value = streaming.dataPoint.values[fieldId] ?: streaming.dataPoint.singleValue ?: return@addConsumer
            onValue(value)
        }
    }

    private fun onRideState(next: RideState) {
        val wasRiding = rideState is RideState.Recording || rideState is RideState.Paused
        val isRiding = next is RideState.Recording || next is RideState.Paused
        val wasActivelyRecording = rideState is RideState.Recording
        if (wasActivelyRecording && next is RideState.Idle && latestCameraState.recording &&
            DjiSettings.read(appContext).rideBookmarks
        ) {
            Log.i(TAG, "Automatic highlight requested reason=Ride end")
            DjiCameraController.addHighlight(appContext, appContext.getString(R.string.highlight_ride_end_feedback))
        }
        rideState = next
        DjiCameraController.setRideActive(appContext, isRiding)

        if (!wasRiding && next is RideState.Recording) {
            startBookmarkSent = false
            batteryAlertArmed = true
            storageAlertArmed = true
            completedClimbs.clear()
            resetThresholdEdges()
            maybeSendStartBookmark()
        }

        if (!isRiding) {
            startBookmarkSent = false
            completedClimbs.clear()
            autoStartRecordingPending = false
            resetThresholdEdges()
        }
    }

    private fun onCameraState(state: DjiCameraState) {
        latestCameraState = state
        val settings = DjiSettings.read(appContext)
        if (state.recording) autoStartRecordingPending = false

        if (settings.actionNotifications && !previousRecording && state.recording) {
            showAlert("dji-recording", appContext.getString(R.string.alert_recording_started), null, R.color.dji_confirm)
            maybeSendStartBookmark()
        } else if (settings.actionNotifications && previousRecording && !state.recording) {
            showAlert("dji-recording", appContext.getString(R.string.alert_recording_stopped), null, R.color.dji_neutral)
        } else if (!previousRecording && state.recording) {
            maybeSendStartBookmark()
        }
        previousRecording = state.recording

        if (
            settings.actionNotifications &&
            previousPhotoState != PhotoCaptureState.COMPLETE &&
            state.photoState == PhotoCaptureState.COMPLETE
        ) {
            showAlert("dji-photo", appContext.getString(R.string.alert_photo_captured), null, R.color.dji_confirm)
        }
        previousPhotoState = state.photoState

        if (state.highlightCount > previousHighlightCount) {
            showAlert("dji-highlight-${state.highlightCount}", appContext.getString(R.string.alert_highlight_added), null, R.color.dji_confirm)
        }
        previousHighlightCount = state.highlightCount

        checkThresholdAlerts(state)
    }

    private fun maybeSendStartBookmark() {
        if (startBookmarkSent || rideState !is RideState.Recording || !latestCameraState.recording) return
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled || !settings.rideBookmarks) return
        startBookmarkSent = true
        requestHighlight(appContext.getString(R.string.highlight_ride_start_feedback), cooldownExempt = true)
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
        powerWasAbove = if (powerWasAbove) watts >= threshold - 50.0 else watts >= threshold
    }

    private fun onSpeed(metersPerSecond: Double) {
        latestSpeedMetersPerSecond = metersPerSecond
        val kph = metersPerSecond * 3.6
        val settings = DjiSettings.read(appContext)
        if (!settings.autoHighlightsEnabled) {
            speedWasAbove = false
            checkHighSpeedDescent()
            publishGpsTelemetry()
            return
        }
        val threshold = settings.maxSpeedKph.toDouble()
        if (settings.maxSpeedHighlights && !speedWasAbove && kph >= threshold) {
            requestHighlight(appContext.getString(R.string.highlight_max_speed_feedback, kph.toInt()))
        }
        speedWasAbove = if (speedWasAbove) kph >= threshold - 3.0 else kph >= threshold
        checkHighSpeedDescent()
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
        hrWasInZoneFive = if (hrWasInZoneFive) heartRate >= threshold - 3 else inZoneFive
    }

    private fun onGrade(grade: Double) {
        latestGradePercent = grade
        checkHighSpeedDescent()
    }

    private fun checkHighSpeedDescent() {
        val speedKph = (latestSpeedMetersPerSecond ?: return) * 3.6
        val grade = latestGradePercent ?: return
        val settings = DjiSettings.read(appContext)
        val active = speedKph >= settings.descentSpeedKph && grade <= -3.0
        if (settings.highSpeedDescentHighlights && !descentWasActive && active) {
            requestHighlight(appContext.getString(R.string.highlight_descent_feedback))
        }
        descentWasActive = if (descentWasActive) {
            speedKph >= settings.descentSpeedKph - 3 && grade <= -2.0
        } else {
            active
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
        if (!DjiSettings.read(appContext).autoHighlightsEnabled) {
            Log.i(TAG, "Automatic highlight skipped reason=$reason autoHighlights=false")
            return
        }
        if (rideState !is RideState.Recording) {
            Log.i(TAG, "Automatic highlight skipped reason=$reason ride=$rideState cameraRecording=${latestCameraState.recording}")
            return
        }
        if (!latestCameraState.recording) {
            val settings = DjiSettings.read(appContext)
            if (settings.autoStartRecording && !autoStartRecordingPending) {
                autoStartRecordingPending = true
                Log.i(TAG, "Automatic recording start requested reason=$reason")
                DjiCameraController.startRecording(appContext)
            } else {
                Log.i(
                    TAG,
                    "Automatic highlight skipped reason=$reason ride=$rideState " +
                        "cameraRecording=${latestCameraState.recording} autoStart=${settings.autoStartRecording} " +
                        "startPending=$autoStartRecordingPending",
                )
            }
            return
        }
        Log.i(TAG, "Automatic highlight requested reason=$reason")
        DjiCameraController.addHighlight(appContext, reason, cooldownLimited = !cooldownExempt)
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
        karooSystem.dispatch(
            InRideAlert(
                id = id,
                icon = R.drawable.ic_launcher_foreground,
                title = title,
                detail = detail,
                autoDismissMs = 2_000L,
                backgroundColor = backgroundColor,
                textColor = R.color.white,
            ),
        )
    }

    private fun resetThresholdEdges() {
        powerWasAbove = false
        speedWasAbove = false
        hrWasInZoneFive = false
        descentWasActive = false
    }

    companion object {
        private const val TAG = "KarooPoC"
    }
}
