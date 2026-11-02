package com.karoopoc.dji

/** One bounded recovery cycle. Owned by the BLE client's main-thread scheduler. */
internal class KeepAwakeAttempt {
    private val sent = linkedMapOf<Int, Long>()
    val count: Int get() = sent.size
    fun sent(sequence: Int, atMs: Long) {
        check(count < MAX_ATTEMPTS)
        sent[sequence] = atMs
    }
    fun response(sequence: Int, payload: ByteArray): Result {
        val at = sent[sequence] ?: return Result.Ignore
        if (payload.size != 1) return Result.Ignore
        return when (payload[0].toInt() and 0xff) {
            0 -> Result.Success(at)
            1 -> Result.InvalidCommand
            else -> Result.Ignore // Missing/unknown/failed results use the bounded timeout path.
        }
    }
    fun contains(sequence: Int) = sent.containsKey(sequence)
    fun canRetry() = count < MAX_ATTEMPTS
    sealed class Result {
        data class Success(val sentAtMs: Long) : Result()
        object InvalidCommand : Result()
        object Ignore : Result()
    }
    companion object {
        const val ACK_TIMEOUT_MS = 5_000L
        const val MAX_ATTEMPTS = 3
    }
}
