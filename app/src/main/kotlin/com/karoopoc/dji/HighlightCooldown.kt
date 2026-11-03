package com.karoopoc.dji

/** Shared by all threshold triggers; timestamps use elapsed realtime, never wall time. */
internal class HighlightCooldown {
    private var lastSentAtMs: Long? = null

    fun allows(nowMs: Long, seconds: Int, exempt: Boolean = false): Boolean {
        if (exempt || seconds == 0) return true
        val last = lastSentAtMs ?: return true
        return nowMs - last >= seconds * 1_000L
    }

    // Only successful sends advance the cooldown. Exempt markers are always allowed,
    // but also space out subsequent automatic threshold highlights.
    fun onSent(nowMs: Long) { lastSentAtMs = nowMs }

    companion object {
        const val DEFAULT_SECONDS = 30
        val OPTIONS_SECONDS = listOf(0, 15, 30, 60)
        fun normalize(seconds: Int): Int = seconds.takeIf { it in OPTIONS_SECONDS } ?: DEFAULT_SECONDS
    }
}
