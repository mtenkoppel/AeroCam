package com.karoopoc.dji

import org.junit.Assert.*
import org.junit.Test

class KeepAwakeAttemptTest {
    @Test fun timeoutNeverQueuesRetriesBehindAStalledWriteOrOnAStaleLink() {
        val cycle = KeepAwakeAttempt()
        cycle.sent(1, 0)
        assertEquals(KeepAwakeAttempt.Timeout.WRITE_STALLED, cycle.timeout(true, false, true))
        assertEquals(KeepAwakeAttempt.Timeout.WRITE_STALLED, cycle.timeout(false, true, true))
        assertEquals(KeepAwakeAttempt.Timeout.LINK_STALE, cycle.timeout(false, false, false))
        assertEquals(KeepAwakeAttempt.Timeout.RETRY, cycle.timeout(false, false, true))
        cycle.sent(2, 5000)
        assertEquals(KeepAwakeAttempt.Timeout.RETRY, cycle.timeout(false, false, true))
        cycle.sent(3, 10000)
        assertEquals(KeepAwakeAttempt.Timeout.EXHAUSTED, cycle.timeout(false, false, true))
    }

    @Test fun lateSuccessUsesOriginalSendTime() {
        val cycle = KeepAwakeAttempt()
        cycle.sent(7, 1000)
        cycle.sent(8, 6000)
        assertEquals(KeepAwakeAttempt.Result.Success(1000), cycle.response(7, byteArrayOf(0)))
        assertEquals(KeepAwakeAttempt.Result.Success(6000), cycle.response(8, byteArrayOf(0)))
    }
    @Test fun unrelatedAndMalformedRepliesAreNotSuccess() {
        val cycle = KeepAwakeAttempt()
        cycle.sent(7, 1000)
        for (payload in listOf(byteArrayOf(), byteArrayOf(0, 0), byteArrayOf(2), byteArrayOf(-1))) {
            assertEquals(KeepAwakeAttempt.Result.Ignore, cycle.response(7, payload))
        }
        assertEquals(KeepAwakeAttempt.Result.Ignore, cycle.response(8, byteArrayOf(0)))
        assertEquals(KeepAwakeAttempt.Result.InvalidCommand, cycle.response(7, byteArrayOf(1)))
    }
    @Test fun retriesAreBoundedAndNewCycleHasNoOldAcknowledgements() {
        val cycle = KeepAwakeAttempt()
        repeat(3) { assertTrue(cycle.canRetry()); cycle.sent(it, it * 5000L) }
        assertFalse(cycle.canRetry())
        assertEquals(KeepAwakeAttempt.Result.Ignore, KeepAwakeAttempt().response(1, byteArrayOf(0)))
    }
}
