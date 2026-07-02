package com.karoopoc.dji

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import kotlin.math.min

object DjiProtocol {
    const val CMD_NO_RESPONSE = 0x00
    const val CMD_RESPONSE_OR_NOT = 0x01
    const val CMD_WAIT_RESULT = 0x02
    const val ACK_NO_RESPONSE = 0x20

    const val CMD_SET_COMMON = 0x00
    const val CMD_ID_VERSION_QUERY = 0x00
    const val CMD_ID_KEY_REPORT = 0x11
    const val CMD_ID_GPS_DATA_PUSH = 0x17
    const val CMD_ID_CONNECTION_REQUEST = 0x19
    const val CMD_ID_POWER_MODE_SWITCH = 0x1A

    const val CMD_SET_CAMERA = 0x1D
    const val CMD_ID_CAMERA_STATUS_PUSH = 0x02
    const val CMD_ID_RECORD_CONTROL = 0x03
    const val CMD_ID_MODE_SWITCH = 0x04
    const val CMD_ID_STATUS_SUBSCRIPTION = 0x05
    const val CMD_ID_NEW_CAMERA_STATUS_PUSH = 0x06

    private const val SOF = 0xAA
    private const val HEADER_LENGTH = 14
    private const val TAIL_LENGTH = 4

    data class Frame(
        val raw: ByteArray,
        val cmdType: Int,
        val seq: Int,
        val cmdSet: Int,
        val cmdId: Int,
        val payload: ByteArray,
    ) {
        val isResponse: Boolean = (cmdType and 0x20) != 0
        val commandName: String = "%02X%02X".format(cmdSet, cmdId)
    }

    data class CameraStatus(
        val mode: Int,
        val status: Int,
        val videoResolution: Int,
        val fps: Int,
        val eisMode: Int,
        val recordTimeSeconds: Int,
        val remainingCapacityMb: Long,
        val remainingPhotos: Long,
        val remainingTimeSeconds: Long,
        val userMode: Int,
        val powerMode: Int,
        val batteryPercent: Int,
    )

    fun createFrame(cmdSet: Int, cmdId: Int, cmdType: Int, payload: ByteArray, seq: Int): ByteArray {
        val frameLength = HEADER_LENGTH + payload.size + TAIL_LENGTH
        val frame = ByteArray(frameLength)
        var offset = 0

        frame[offset++] = SOF.toByte()
        putU16(frame, offset, frameLength)
        offset += 2
        frame[offset++] = cmdType.toByte()
        frame[offset++] = 0x00
        frame[offset++] = 0x00
        frame[offset++] = 0x00
        frame[offset++] = 0x00
        putU16(frame, offset, seq)
        offset += 2

        val crc16 = crc16(frame, offset)
        putU16(frame, offset, crc16)
        offset += 2

        frame[offset++] = cmdSet.toByte()
        frame[offset++] = cmdId.toByte()
        payload.copyInto(frame, offset)
        offset += payload.size

        val crc32 = crc32(frame, offset)
        putU32(frame, offset, crc32)

        return frame
    }

    fun parseFrame(raw: ByteArray): Frame? {
        if (raw.size < 16 || raw[0].toUByteInt() != SOF) return null

        val expectedLength = readU16(raw, 1) and 0x03FF
        if (expectedLength != raw.size) return null

        val receivedCrc16 = readU16(raw, 10)
        if (receivedCrc16 != crc16(raw, 10)) return null

        val receivedCrc32 = readU32(raw, raw.size - 4)
        if (receivedCrc32 != crc32(raw, raw.size - 4)) return null

        val cmdSet = raw[12].toUByteInt()
        val cmdId = raw[13].toUByteInt()
        val payload = raw.copyOfRange(14, raw.size - 4)

        return Frame(
            raw = raw,
            cmdType = raw[3].toUByteInt(),
            seq = readU16(raw, 8),
            cmdSet = cmdSet,
            cmdId = cmdId,
            payload = payload,
        )
    }

    fun connectionRequestPayload(
        controllerId: ByteArray,
        verifyMode: Int,
        verifyData: Int,
    ): ByteArray {
        require(controllerId.size == 6) { "controllerId must be 6 bytes" }

        return ByteArray(33).also { payload ->
            putU32(payload, 0, 0x12345678)
            payload[4] = 6
            controllerId.copyInto(payload, destinationOffset = 5)
            putU32(payload, 21, 0)
            payload[25] = 0
            payload[26] = verifyMode.toByte()
            putU16(payload, 27, verifyData)
        }
    }

    fun connectionResponsePayload(controllerDeviceId: Long, cameraNumber: Int): ByteArray {
        return ByteArray(9).also { payload ->
            putU32(payload, 0, controllerDeviceId)
            payload[4] = 0
            payload[5] = cameraNumber.toByte()
        }
    }

    fun versionQueryPayload(): ByteArray = ByteArray(0)

    fun statusSubscriptionPayload(): ByteArray {
        return byteArrayOf(
            0x03, // Periodic + push once after state changes.
            20, // 2Hz, expressed in 0.1Hz units.
            0x00,
            0x00,
            0x00,
            0x00,
        )
    }

    fun photoModePayload(cameraCommandDeviceId: Long): ByteArray {
        return modeSwitchPayload(cameraCommandDeviceId, mode = 0x05)
    }

    fun qsButtonShortPressPayload(): ByteArray {
        return byteArrayOf(
            0x02, // key_code: QS/highlight
            0x01, // mode: event
            0x00,
            0x00  // key_value: short press (2 bytes)
        )
    }

    fun snapshotButtonShortPressPayload(): ByteArray {
        return byteArrayOf(
            0x03, // Snapshot key.
            0x01, // Key event mode.
            0x00,
            0x00, // Short press.
        )
    }

    fun videoModePayload(cameraCommandDeviceId: Long): ByteArray {
        return modeSwitchPayload(cameraCommandDeviceId, mode = 0x01)
    }

    private fun modeSwitchPayload(cameraCommandDeviceId: Long, mode: Int): ByteArray {
        return ByteArray(9).also { payload ->
            putU32(payload, 0, cameraCommandDeviceId)
            payload[4] = mode.toByte()
            payload[5] = 0x01
            payload[6] = 0x47
            payload[7] = 0x39
            payload[8] = 0x36
        }
    }

    fun recordControlPayload(cameraCommandDeviceId: Long, start: Boolean): ByteArray {
        return ByteArray(9).also { payload ->
            putU32(payload, 0, cameraCommandDeviceId)
            payload[4] = if (start) 0x00 else 0x01
        }
    }

    fun gpsPayload(telemetry: DjiGpsTelemetry, now: ZonedDateTime = ZonedDateTime.now()): ByteArray {
        return ByteBuffer.allocate(48)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(now.year * 10_000 + now.monthValue * 100 + now.dayOfMonth)
            .putInt(now.hour * 10_000 + now.minute * 100 + now.second)
            .putInt((telemetry.longitude * 10_000_000).toInt())
            .putInt((telemetry.latitude * 10_000_000).toInt())
            .putInt((telemetry.altitudeMeters * 1_000).toInt())
            .putFloat((telemetry.speedNorthMetersPerSecond * 100).toFloat())
            .putFloat((telemetry.speedEastMetersPerSecond * 100).toFloat())
            .putFloat((telemetry.speedDownMetersPerSecond * 100).toFloat())
            .putInt((telemetry.verticalAccuracyMeters * 1_000).toInt().coerceAtLeast(0))
            .putInt((telemetry.horizontalAccuracyMeters * 1_000).toInt().coerceAtLeast(0))
            .putInt((telemetry.speedAccuracyMetersPerSecond * 100).toInt().coerceAtLeast(0))
            .putInt(telemetry.satelliteCount.coerceAtLeast(0))
            .array()
    }

    fun recordButtonShortPressPayload(): ByteArray {
        return byteArrayOf(
            0x01, // Record button.
            0x01, // Key event mode.
            0x00,
            0x00, // Short press.
        )
    }

    fun sleepModePayload(): ByteArray = byteArrayOf(0x03)

    fun parseCameraStatus(payload: ByteArray): CameraStatus? {
        if (payload.size < 38) return null

        return CameraStatus(
            mode = payload[0].toUByteInt(),
            status = payload[1].toUByteInt(),
            videoResolution = payload[2].toUByteInt(),
            fps = payload[3].toUByteInt(),
            eisMode = payload[4].toUByteInt(),
            recordTimeSeconds = readU16(payload, 5),
            remainingCapacityMb = readU32(payload, 15),
            remainingPhotos = readU32(payload, 19),
            remainingTimeSeconds = readU32(payload, 23),
            userMode = payload[27].toUByteInt(),
            powerMode = payload[28].toUByteInt(),
            batteryPercent = payload[37].toUByteInt(),
        )
    }

    fun parseNewStatusText(payload: ByteArray): String? {
        if (payload.size < 46) return null

        val modeNameLength = min(payload[1].toUByteInt(), 20)
        val modeParamLength = min(payload[24].toUByteInt(), 20)
        val modeName = payload.copyOfRange(2, 2 + modeNameLength).toAscii()
        val modeParam = payload.copyOfRange(25, 25 + modeParamLength).toAscii()
        return "modeName=$modeName modeParam=$modeParam"
    }

    fun readU16(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toUByteInt() or (bytes[offset + 1].toUByteInt() shl 8)
    }

    fun readU32(bytes: ByteArray, offset: Int): Long {
        return bytes[offset].toUByteInt().toLong() or
            (bytes[offset + 1].toUByteInt().toLong() shl 8) or
            (bytes[offset + 2].toUByteInt().toLong() shl 16) or
            (bytes[offset + 3].toUByteInt().toLong() shl 24)
    }

    fun cameraCommandDeviceId(cameraDeviceIdFromCamera: Long?): Long {
        val raw = cameraDeviceIdFromCamera ?: 0xFF330000L
        return if (raw <= 0xFFFFL) raw shl 16 else raw
    }

    fun modeName(mode: Int): String {
        return when (mode) {
            0x00 -> "Slow Motion"
            0x01 -> "Video"
            0x02 -> "Timelapse"
            0x05 -> "Photo"
            0x0A -> "Hyperlapse"
            0x1A -> "Live Streaming"
            0x23 -> "UVC Live Streaming"
            0x28 -> "SuperNight"
            0x34 -> "Subject Tracking"
            else -> "Unknown"
        }
    }

    fun statusName(status: Int): String {
        return when (status) {
            0x00 -> "Screen off"
            0x01 -> "Live view"
            0x02 -> "Playback"
            0x03 -> "Photo/video in progress"
            0x05 -> "Pre-recording"
            else -> "Unknown"
        }
    }

    fun hex(bytes: ByteArray): String {
        return bytes.joinToString(separator = " ") { "%02X".format(it.toUByteInt()) }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun crc16(bytes: ByteArray, length: Int): Int {
        var crc = 0x3AA3
        for (index in 0 until length) {
            crc = crc xor bytes[index].toUByteInt()
            repeat(8) {
                crc = if ((crc and 0x0001) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
            crc = crc and 0xFFFF
        }
        return crc
    }

    private fun crc32(bytes: ByteArray, length: Int): Long {
        var crc = 0x00003AA3L
        for (index in 0 until length) {
            crc = crc xor bytes[index].toUByteInt().toLong()
            repeat(8) {
                crc = if ((crc and 0x00000001L) != 0L) {
                    (crc ushr 1) xor 0xEDB88320L
                } else {
                    crc ushr 1
                }
            }
            crc = crc and 0xFFFFFFFFL
        }
        return crc
    }

    private fun Byte.toUByteInt(): Int = toInt() and 0xFF

    private fun ByteArray.toAscii(): String {
        return String(this, StandardCharsets.US_ASCII)
            .replace('\u0000', ' ')
            .trim()
    }
}
