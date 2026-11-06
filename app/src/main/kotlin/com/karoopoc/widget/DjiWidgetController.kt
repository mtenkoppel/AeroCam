package com.karoopoc.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.karoopoc.R
import com.karoopoc.dji.CameraConnectionState
import com.karoopoc.dji.DjiCameraController
import com.karoopoc.dji.DjiCameraMode
import com.karoopoc.dji.DjiCameraState
import com.karoopoc.dji.PhotoCaptureState
import java.util.concurrent.CopyOnWriteArraySet

object DjiWidgetController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(DjiWidgetViewState) -> Unit>()
    private var cameraState = DjiCameraController.state()
    private var cancelCameraObservation: (() -> Unit)? = null
    private var controlFeedback: String? = null
    private var sleepConfirmation = false
    private var feedbackClear: Runnable? = null
    private var confirmationClear: Runnable? = null
    private val connectPrompts = linkedMapOf<ConnectPrompt, Runnable>()
    private var photoAwaitingResponse: String? = null
    private var videoAwaitingResponse: String? = null
    private var videoSettingsRefresh: Runnable? = null
    private var photoAwaitingClear: Runnable? = null
    private var videoAwaitingClear: Runnable? = null
    private var photoAwaitingTick: Runnable? = null
    private var videoAwaitingTick: Runnable? = null
    private var photoAwaitingPhase = 0
    private var videoAwaitingPhase = 0

    fun state(): DjiCameraState = cameraState

    fun observe(context: Context, listener: (DjiWidgetViewState) -> Unit): () -> Unit {
        listeners.add(listener)
        if (cancelCameraObservation == null) {
            cancelCameraObservation = DjiCameraController.observe(context.applicationContext) { next ->
                val previous = cameraState
                cameraState = next
                if (next.connection != CameraConnectionState.CONNECTED || next.recording || next.hasCamControlVideoProfile()) {
                    clearVideoSettingsRefresh()
                }
                if (next.connection == CameraConnectionState.CONNECTED) {
                    clearAllConnectPrompts()
                }
                if (next.connection != CameraConnectionState.CONNECTED && sleepConfirmation) {
                    cancelSleep()
                }
                if (next.recording != previous.recording) {
                    clearVideoAwaitingResponse()
                }
                if (
                    next.photoState != previous.photoState &&
                    next.photoState != PhotoCaptureState.IDLE
                ) {
                    clearPhotoAwaitingResponse()
                }
                emit()
            }
        } else {
            listener(viewState())
        }
        return {
            listeners.remove(listener)
            if (listeners.isEmpty()) disconnect()
        }
    }

    fun connect(context: Context) {
        if (!state().configured) return
        toggleConnectPrompt(context, ConnectPrompt.CONTROL)
    }

    fun refreshVideoSettings(context: Context) {
        val camera = state()
        if (camera.connection != CameraConnectionState.CONNECTED || camera.recording ||
            camera.hasCamControlVideoProfile() || videoSettingsRefresh != null
        ) return
        videoSettingsRefresh = Runnable {
            videoSettingsRefresh = null
            emit()
        }.also { mainHandler.postDelayed(it, VIDEO_SETTINGS_REFRESH_MS) }
        if (camera.cameraMode != DjiCameraMode.VIDEO) DjiCameraController.selectVideoMode(context)
        emit()
    }

    private fun clearVideoSettingsRefresh() {
        videoSettingsRefresh?.let(mainHandler::removeCallbacks)
        videoSettingsRefresh = null
    }

    fun triggerPhoto(context: Context) {
        if (state().connection == CameraConnectionState.CONNECTED) {
            if (photoAwaitingResponse != null) return
            startPhotoAwaitingResponse()
            DjiCameraController.takePhoto(context)
            return
        }
        if (state().configured) {
            toggleConnectPrompt(context, ConnectPrompt.PHOTO)
        }
    }

    fun toggleRecording(context: Context) {
        if (state().connection == CameraConnectionState.CONNECTED) {
            if (videoAwaitingResponse != null) return
            startVideoAwaitingResponse()
            DjiCameraController.toggleRecording(context)
            return
        }
        if (state().configured) {
            toggleConnectPrompt(context, ConnectPrompt.VIDEO)
        }
    }

    fun addHighlight(context: Context) = DjiCameraController.addHighlight(context)

    fun toggleKeepAwake(context: Context) {
        val enabled = !state().keepAwakeEnabled
        showControlFeedback(
            context.getString(if (enabled) R.string.widget_keep_awake_on else R.string.widget_keep_awake_off),
        )
        DjiCameraController.setKeepAwake(context, enabled)
    }

    fun requestSleep() {
        clearControlFeedback()
        sleepConfirmation = true
        confirmationClear?.let(mainHandler::removeCallbacks)
        confirmationClear = Runnable { cancelSleep() }.also {
            mainHandler.postDelayed(it, CONFIRMATION_MS)
        }
        emit()
    }

    fun confirmSleep(context: Context) {
        clearVideoSettingsRefresh()
        confirmationClear?.let(mainHandler::removeCallbacks)
        confirmationClear = null
        sleepConfirmation = false
        clearAllConnectPrompts()
        cameraState = cameraState.copy(
            connection = CameraConnectionState.DISCONNECTED,
            keepAwakeEnabled = false,
            keepAwakeActive = false,
            recording = false,
            recordingDurationSeconds = 0,
        )
        DjiCameraController.sleep(context)
        emit()
    }

    fun cancelSleep() {
        confirmationClear?.let(mainHandler::removeCallbacks)
        confirmationClear = null
        if (!sleepConfirmation) return
        sleepConfirmation = false
        emit()
    }

    fun disconnect() {
        cancelCameraObservation?.invoke()
        cancelCameraObservation = null
    }

    fun refresh() {
        emit()
    }

    private fun showControlFeedback(message: String) {
        controlFeedback = message
        feedbackClear?.let(mainHandler::removeCallbacks)
        feedbackClear = Runnable { clearControlFeedback() }.also {
            mainHandler.postDelayed(it, CONTROL_FEEDBACK_MS)
        }
        emit()
    }

    private fun clearControlFeedback() {
        feedbackClear?.let(mainHandler::removeCallbacks)
        feedbackClear = null
        if (controlFeedback == null) return
        controlFeedback = null
        emit()
    }

    private fun toggleConnectPrompt(context: Context, prompt: ConnectPrompt) {
        if (connectPrompts.containsKey(prompt)) {
            clearConnectPrompt(prompt)
            return
        }

        clearAllConnectPrompts()
        val clearRunnable = Runnable { clearConnectPrompt(prompt) }
        connectPrompts[prompt] = clearRunnable
        mainHandler.postDelayed(clearRunnable, CONNECT_PROMPT_MS)
        DjiCameraController.connect(context)
        emit()
    }

    private fun clearConnectPrompt(prompt: ConnectPrompt) {
        val runnable = connectPrompts.remove(prompt) ?: return
        mainHandler.removeCallbacks(runnable)
        emit()
    }

    private fun clearAllConnectPrompts() {
        if (connectPrompts.isEmpty()) return
        connectPrompts.values.forEach(mainHandler::removeCallbacks)
        connectPrompts.clear()
        emit()
    }

    private fun viewState(): DjiWidgetViewState = DjiWidgetViewState(
        camera = cameraState,
        controlFeedback = controlFeedback,
        sleepConfirmation = sleepConfirmation,
        connectPrompts = connectPrompts.keys.toSet(),
        photoAwaitingResponse = photoAwaitingResponse,
        videoAwaitingResponse = videoAwaitingResponse,
        refreshingVideoSettings = videoSettingsRefresh != null,
    )

    private fun emit() {
        val next = viewState()
        listeners.forEach { it(next) }
    }

    private fun startPhotoAwaitingResponse() {
        startAwaitingResponse(
            setDots = { photoAwaitingResponse = it },
            setPhase = { photoAwaitingPhase = it },
            getPhase = { photoAwaitingPhase },
            setTick = { photoAwaitingTick = it },
            clearTick = {
                photoAwaitingTick?.let(mainHandler::removeCallbacks)
                photoAwaitingTick = null
            },
            setClear = { photoAwaitingClear = it },
            clearNow = ::clearPhotoAwaitingResponse,
        )
    }

    private fun startVideoAwaitingResponse() {
        startAwaitingResponse(
            setDots = { videoAwaitingResponse = it },
            setPhase = { videoAwaitingPhase = it },
            getPhase = { videoAwaitingPhase },
            setTick = { videoAwaitingTick = it },
            clearTick = {
                videoAwaitingTick?.let(mainHandler::removeCallbacks)
                videoAwaitingTick = null
            },
            setClear = { videoAwaitingClear = it },
            clearNow = ::clearVideoAwaitingResponse,
        )
    }

    private fun startAwaitingResponse(
        setDots: (String?) -> Unit,
        setPhase: (Int) -> Unit,
        getPhase: () -> Int,
        setTick: (Runnable?) -> Unit,
        clearTick: () -> Unit,
        setClear: (Runnable?) -> Unit,
        clearNow: () -> Unit,
    ) {
        clearNow()
        setPhase(0)
        setDots(waitingDots(0))
        clearTick()
        val tick = object : Runnable {
            override fun run() {
                val nextPhase = (getPhase() + 1) % 3
                setPhase(nextPhase)
                setDots(waitingDots(nextPhase))
                emit()
                mainHandler.postDelayed(this, WAITING_TICK_MS)
            }
        }
        setTick(tick)
        mainHandler.postDelayed(tick, WAITING_TICK_MS)
        val clear = Runnable { clearNow() }
        setClear(clear)
        mainHandler.postDelayed(clear, WAITING_RESPONSE_TIMEOUT_MS)
        emit()
    }

    private fun clearPhotoAwaitingResponse() {
        photoAwaitingClear?.let(mainHandler::removeCallbacks)
        photoAwaitingClear = null
        photoAwaitingTick?.let(mainHandler::removeCallbacks)
        photoAwaitingTick = null
        photoAwaitingPhase = 0
        if (photoAwaitingResponse == null) return
        photoAwaitingResponse = null
        emit()
    }

    private fun clearVideoAwaitingResponse() {
        videoAwaitingClear?.let(mainHandler::removeCallbacks)
        videoAwaitingClear = null
        videoAwaitingTick?.let(mainHandler::removeCallbacks)
        videoAwaitingTick = null
        videoAwaitingPhase = 0
        if (videoAwaitingResponse == null) return
        videoAwaitingResponse = null
        emit()
    }

    private fun waitingDots(phase: Int): String = when (phase) {
        0 -> "."
        1 -> ".."
        else -> "..."
    }

    private const val CONTROL_FEEDBACK_MS = 1_500L
    private const val CONNECT_PROMPT_MS = 5_000L
    private const val CONFIRMATION_MS = 5_000L
    private const val WAITING_TICK_MS = 320L
    private const val WAITING_RESPONSE_TIMEOUT_MS = 2_500L
    private const val VIDEO_SETTINGS_REFRESH_MS = 8_000L
}

enum class ConnectPrompt {
    CONTROL,
    PHOTO,
    VIDEO,
}
