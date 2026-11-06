package com.karoopoc.dji

/** Monotonic time, confirmed clip identity, and tri-state telemetry. No Android scheduling. */
internal class AutoStopPolicy {
    private var clip = 0L
    private var delay = 0
    private var quietSince: Long? = null
    private var sent = false
    fun event() { quietSince = null; sent = false }
    fun update(now: Long, clipId: Long, enabled: Boolean, active: Boolean?, seconds: Int): Boolean {
        if (clip != clipId || delay != seconds || !enabled || clipId == 0L) {
            clip = clipId
            delay = seconds
            quietSince = null
            sent = false
        }
        if (!enabled || clipId == 0L) return false
        if (active != false) { quietSince = null; sent = false; return false }
        if (sent) return false
        val since = quietSince ?: now.also { quietSince = it }
        if (now - since < seconds.coerceAtLeast(1) * 1000L) return false
        sent = true
        return true
    }
}

/** Ownership is deliberately not persisted across disconnects or process restarts. */
internal class RecordingOrigin {
    enum class Feedback { STARTED, STOPPED }
    var clip = 0L; private set
    private var serial = 0L
    private var startUntil: Long? = null
    var stopping = false; private set
    private var recording = false
    private var lastDuration: Int? = null
    fun startPending(now: Long): Boolean = startUntil?.let { now <= it } == true
    fun requestStart(now: Long) { startUntil = now + 15000 }
    fun requestStop() { stopping = clip != 0L }
    fun cancelStop() { stopping = false }
    fun cancel() { clip = 0; startUntil = null; stopping = false }
    fun status(next: Boolean, now: Long, durationSeconds: Int? = null): Feedback? {
        // A stop/start on the camera can happen between status packets. A reset timer
        // means ownership is uncertain even if we never observed recording=false.
        if (next && recording && durationSeconds != null && lastDuration?.let { durationSeconds < it } == true) cancel()
        lastDuration = durationSeconds.takeIf { next }
        val feedback = when {
            next && !recording && startUntil?.let { now <= it } == true -> {
                clip = ++serial; Feedback.STARTED
            }
            !next && recording && stopping && clip != 0L -> Feedback.STOPPED
            else -> null
        }
        if (next) startUntil = null
        if (!next && recording) cancel()
        recording = next
        return feedback
    }
}

internal class AutomaticPhotoPolicy {
    private var last: Long? = null
    fun request(now: Long, ready: Boolean, intervalSeconds: Int): Boolean {
        if (!ready || last?.let { now - it < intervalSeconds.coerceAtLeast(1) * 1000L } == true) return false
        last = now
        return true
    }
}
