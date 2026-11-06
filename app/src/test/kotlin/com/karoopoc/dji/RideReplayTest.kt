package com.karoopoc.dji

import android.os.Bundle
import io.hammerhead.karooext.aidl.IKarooSystem
import io.hammerhead.karooext.aidl.IHandler
import io.hammerhead.karooext.internal.bundleWithSerializable
import io.hammerhead.karooext.internal.serializableFromBundle
import io.hammerhead.karooext.models.*
import android.os.Looper
import android.os.SystemClock
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.RideState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

/** Local-only FIT fixture. Only sensor input and physical camera are simulated; opt in with AEROCAM_REPLAY_DIR. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
@LooperMode(LooperMode.Mode.PAUSED)
class RideReplayTest {
    private lateinit var directory: File
    private fun field(o: Any, name: String) = o.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun invoke(o: Any, name: String, type: Class<*>, value: Any) =
        o.javaClass.getDeclaredMethod(name, type).apply { isAccessible = true }.invoke(o, value)

    @Test fun replayOriginalOrder() = replay(false)
    @Test fun replayReversedOrder() = replay(true)

    private fun replay(reverse: Boolean) {
        val path = System.getenv("AEROCAM_REPLAY_DIR")
        org.junit.Assume.assumeTrue("Set AEROCAM_REPLAY_DIR to opt into private FIT replay", !path.isNullOrBlank())
        directory = File(path!!)
        val config = org.json.JSONObject(directory.resolve("settings.json").readText())
        val mode = AutomationMode.valueOf(config.getString("mode").uppercase())
        val honorPauses = config.optBoolean("honorPauses", true)
        val photoInterval = config.optInt("photoIntervalSeconds", 120)
        val context = RuntimeEnvironment.getApplication()
        DjiSettings.resetAll(context)
        val prefs = DjiSettings.preferences(context).edit()
            .putString("camera_address", "00:00:00:00:00:00").putBoolean("protocol_connected_once", true)
            .putString(DjiSettings.KEY_AUTOMATION_MODE, mode.name)
            .putBoolean(DjiSettings.KEY_AUTO_HIGHLIGHTS_ENABLED, config.optBoolean("enabled", true))
            .putBoolean(DjiSettings.KEY_AUTO_STOP, config.optBoolean("autoStop", true))
            .putInt(DjiSettings.KEY_STOP_SECONDS, config.optInt("stopAfterSeconds", 60))
            .putInt(DjiSettings.KEY_PHOTO_INTERVAL_SECONDS, photoInterval)
            .putBoolean(DjiSettings.KEY_ADD_HIGHLIGHTS, config.optBoolean("highlights", true))
            .putBoolean(DjiSettings.KEY_MAX_SPEED_HIGHLIGHTS, config.has("speedKph"))
            .putInt(DjiSettings.KEY_MAX_SPEED_KPH, config.optInt("speedKph", 40))
            .putBoolean(DjiSettings.KEY_PEAK_HEART_RATE_HIGHLIGHTS, config.has("heartRateBpm"))
            .putInt(DjiSettings.KEY_PEAK_HEART_RATE_BPM, config.optInt("heartRateBpm", 175))
            .putBoolean(DjiSettings.KEY_UPHILL_HIGHLIGHTS, config.has("uphillGrade"))
            .putInt(DjiSettings.KEY_UPHILL_GRADE_PERCENT, config.optInt("uphillGrade", 5))
            .putBoolean(DjiSettings.KEY_UPHILL_REQUIRE_SPEED, config.optBoolean("uphillRequireSpeed", false))
            .putInt(DjiSettings.KEY_UPHILL_SPEED_KPH, config.optInt("uphillSpeedKph", 20))
            .putBoolean(DjiSettings.KEY_HIGH_SPEED_DESCENT_HIGHLIGHTS, config.has("downhillGrade"))
            .putInt(DjiSettings.KEY_DESCENT_GRADE_PERCENT, config.optInt("downhillGrade", -3))
            .putInt(DjiSettings.KEY_DESCENT_SPEED_KPH, config.optInt("downhillSpeedKph", 45))
            .putBoolean(DjiSettings.KEY_DESCENT_REQUIRE_SPEED, config.optBoolean("downhillRequireSpeed", true))
            .putBoolean(DjiSettings.KEY_KEEP_AWAKE_ENABLED, false)
        prefs.commit()
        shadowOf(context).declareComponentUnbindable(android.content.ComponentName.createRelative("io.hammerhead.appstore", ".service.AppStoreService"))
        val serviceControl = Robolectric.buildService(DjiCameraService::class.java).create()
        val service = serviceControl.get()
        val client = DjiOsmoBleClient(context, {}, stateChanged = { DjiCameraController.publish(it) })
        field(service, "client").set(service, client)
        field(client, "cameraState").set(client, DjiCameraState(configured = true, connection = CameraConnectionState.CONNECTED))
        field(client, "protocolConnected").setBoolean(client, true)
        // Hold the real GATT drain at its physical-I/O boundary. Test drains PendingWrite below.
        field(client, "writeInProgress").setBoolean(client, true)
        val consumers = mutableMapOf<KarooEventParams, IHandler>()
        val karoo = KarooSystemService(context)
        val controller = object : IKarooSystem.Stub() {
            override fun libVersion() = "1.1.9"
            override fun info() = Bundle()
            override fun dispatchEffect(bundle: Bundle) {}
            override fun removeEventConsumer(id: String) {}
            override fun addEventConsumer(id: String, bundle: Bundle, handler: IHandler) {
                consumers[bundle.serializableFromBundle<KarooEventParams>()!!] = handler
            }
        }
        field(karoo, "controller").set(karoo, controller)
        val integration = DjiKarooIntegration(context, karoo)
        fun ride(state: RideState) = consumers.getValue(RideState.Params).onNext(state.bundleWithSerializable("fit-replay"))
        val main = shadowOf(Looper.getMainLooper())
        val startClock = SystemClock.elapsedRealtime()
        var cameraRecording = false
        var recordingSince = 0L
        var startReason = ""
        var photoReason = ""
        var cameraMode = 1
        val photoTimes = mutableListOf<Long>()
        val log = mutableListOf("ms\tkind\tdetail\townedClip")
        fun log(kind: String, detail: String) {
            log += "${SystemClock.elapsedRealtime() - startClock}\t$kind\t$detail\t${DjiCameraController.state().automaticRecordingId}"
        }
        fun status() {
            field(client, "lastCameraRxAtMs").setLong(client, SystemClock.elapsedRealtime())
            val payload = ByteArray(38)
            payload[0] = cameraMode.toByte(); payload[1] = if (cameraRecording) 3 else 0; payload[37] = 95
            val seconds = if (cameraRecording) ((SystemClock.elapsedRealtime() - recordingSince) / 1000).toInt() else 0
            payload[5] = seconds.toByte(); payload[6] = (seconds shr 8).toByte()
            invoke(client, "handleCameraStatus", DjiProtocol.Frame::class.java,
                DjiProtocol.Frame(byteArrayOf(), 0, 1, DjiProtocol.CMD_SET_CAMERA, DjiProtocol.CMD_ID_CAMERA_STATUS_PUSH, payload))
        }
        fun settle() {
            repeat(25) {
                main.idle()
                var changed = false
                while (true) {
                    val intent = shadowOf(context).nextStartedService ?: break
                    changed = true
                    if (intent.action == DjiCameraService.ACTION_AUTO_START) startReason = intent.getStringExtra(DjiCameraService.EXTRA_AUTOMATION_REASON).orEmpty()
                    log("INTENT", intent.action.orEmpty() + (intent.getStringExtra(DjiCameraService.EXTRA_HIGHLIGHT_FEEDBACK)?.let { ":$it" } ?: ""))
                    // Application startup is replaced by an already paired, connected fake camera.
                    val photoBefore = field(client, "photoState").get(client)
                    if (intent.action != DjiCameraService.ACTION_START) service.onStartCommand(intent, 0, 1)
                    if (intent.action == DjiCameraService.ACTION_AUTO_PHOTO) {
                        val reason = intent.getStringExtra(DjiCameraService.EXTRA_AUTOMATION_REASON).orEmpty()
                        val accepted = photoBefore != PhotoCaptureState.REQUESTED && field(client, "photoState").get(client) == PhotoCaptureState.REQUESTED
                        if (accepted) photoReason = reason
                        log(if(accepted) "PHOTO_ACCEPTED" else "PHOTO_SKIPPED", reason)
                    }
                }
                main.idle()
                @Suppress("UNCHECKED_CAST") val queue = field(client, "writeQueue").get(client) as ArrayDeque<Any>
                while (queue.isNotEmpty()) {
                    changed = true
                    val write = queue.removeFirst()
                    @Suppress("UNCHECKED_CAST") val valid = field(write, "automaticValid").get(write) as (() -> Boolean)?
                    val accepted = valid?.invoke() != false
                    val frame = DjiProtocol.parseFrame(field(write, "frame").get(write) as ByteArray)!!
                    @Suppress("UNCHECKED_CAST") val done = field(write, "onComplete").get(write) as (Boolean) -> Unit
                    log("WIRE", "${field(write, "label").get(write)}:${frame.raw.joinToString("") { "%02x".format(it) }}:accepted=$accepted")
                    // Complete the actual write callback before the simulated camera status arrives.
                    done(accepted)
                    if (accepted && frame.cmdSet == DjiProtocol.CMD_SET_CAMERA && frame.cmdId == DjiProtocol.CMD_ID_MODE_SWITCH) {
                        cameraMode = frame.payload[4].toInt()
                        status()
                    }
                    if (accepted && frame.cmdSet == DjiProtocol.CMD_SET_COMMON && frame.cmdId == DjiProtocol.CMD_ID_KEY_REPORT && frame.payload.contentEquals(DjiProtocol.recordButtonShortPressPayload())) {
                        assertEquals("Shutter must be in photo mode", 5, cameraMode)
                        photoTimes += SystemClock.elapsedRealtime() - startClock
                        log("PHOTO", photoReason)
                        status()
                    }
                    if (accepted && frame.cmdSet == DjiProtocol.CMD_SET_CAMERA && frame.cmdId == DjiProtocol.CMD_ID_RECORD_CONTROL) {
                        val recording = frame.payload[4].toInt() == 0
                        assertNotEquals("Repeated start/stop command with no state change", cameraRecording, recording)
                        log(if (recording) "START" else "STOP", if(recording) startReason else "Quiet for 60 seconds")
                        cameraRecording = recording
                        if (recording) recordingSince = SystemClock.elapsedRealtime()
                        status()
                    }
                }
                if (!changed) return
            }
            fail("I/O did not settle")
        }
        status(); integration.start(); settle()
        ride(RideState.Recording); settle()
        val rows = directory.resolve("replay.tsv").readLines().filter { it.isNotBlank() }
        for (line in rows) {
            val c = line.split('\t'); val sec = c[0].toLong(); val target = startClock + sec * 1000
            // 100ms virtual steps preserve the production 700ms mode-change delay and 1s ticker.
            while (SystemClock.elapsedRealtime() < target) {
                main.idleFor(Duration.ofMillis(minOf(100, target - SystemClock.elapsedRealtime()))); settle()
            }
            status(); settle()
            if (honorPauses && c[4] != "-") {
                val next: RideState = when (c[4]) { "start" -> RideState.Recording; "end" -> RideState.Idle; "manual_stop" -> RideState.Paused(auto = false); else -> RideState.Paused(auto = true) }
                val ownedBefore = DjiCameraController.state().automaticRecordingId
                ride(next); settle()
                log("RIDE", c[4])
                if (cameraRecording && ownedBefore != 0L && DjiCameraController.state().automaticRecordingId == 0L) log("HANDOVER", "pause revoked automatic ownership; camera still recording")
            }
            val samples = listOf(Triple("onSpeed", DataType.Type.SPEED, c[1].toDoubleOrNull()?.div(3.6)),
                Triple("onHeartRate", DataType.Type.HEART_RATE, c[2].toDoubleOrNull()),
                Triple("onGrade", DataType.Type.ELEVATION_GRADE, c[3].toDoubleOrNull()))
            for ((method, type, value) in if (reverse) samples.reversed() else samples) {
                if (value != null) {
                    val event = OnStreamState(StreamState.Streaming(DataPoint(type, mapOf(DataType.Field.SINGLE to value))))
                    consumers.getValue(OnStreamState.StartStreaming(type)).onNext(event.bundleWithSerializable("fit-replay"))
                }
            }
            settle()
        }
        log("END", "recording=$cameraRecording")
        val name = mode.name.lowercase() + if(reverse) "-reverse" else ""
        directory.resolve("production-$name.tsv").writeText(log.joinToString("\n") + "\n")
        if (mode == AutomationMode.PHOTOS) {
            assertFalse("Photo mode must not start a video", log.any { it.contains("\tSTART\t") })
            assertTrue("Successful photo commands must respect the interval", photoTimes.zipWithNext().all { (a,b) -> b-a >= photoInterval * 1000L })
            assertFalse(cameraRecording)
        }
        integration.stop(); settle()
        println("PRODUCTION REPLAY $name: ${photoTimes.size} photos; ${log.count { it.contains("\tSTART\t") }} starts, ${log.count { it.contains("\tSTOP\t") }} stops")
    }
}
