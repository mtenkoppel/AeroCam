package com.karoopoc.dji

/** Shared duration for AeroCam notifications; longer requested durations are retained. */
internal object NotificationDuration {
    const val MINIMUM_MS = 7_000L

    fun atLeastMinimum(requestedMs: Long = MINIMUM_MS): Long = requestedMs.coerceAtLeast(MINIMUM_MS)
}

/**
 * Serializes AeroCam's notifications so a later alert cannot replace one before its timeout.
 * Callers must confine access and scheduled callbacks to the same thread.
 * The host can still dismiss or interrupt alerts independently of this queue.
 */
internal class RideNotificationQueue<T>(
    private val show: (T, Long) -> Unit,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
) {
    private data class Entry<T>(val value: T, val durationMs: Long)

    private val pending = ArrayDeque<Entry<T>>()
    private var showing = false
    private var generation = 0

    fun enqueue(value: T, requestedDurationMs: Long = NotificationDuration.MINIMUM_MS) {
        pending.addLast(Entry(value, NotificationDuration.atLeastMinimum(requestedDurationMs)))
        if (!showing) showNext()
    }

    /** Discard stale waiting alerts on pause/end, retaining the current alert's time slot. */
    fun clearPending() {
        pending.clear()
    }

    fun clear() {
        generation++
        cancelScheduled()
        pending.clear()
        showing = false
    }

    private fun showNext() {
        val next = pending.removeFirstOrNull()
        if (next == null) {
            showing = false
            return
        }
        showing = true
        val scheduledGeneration = generation
        show(next.value, next.durationMs)
        schedule(next.durationMs) {
            if (scheduledGeneration == generation) showNext()
        }
    }
}
