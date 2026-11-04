package com.karoopoc.dji

import org.junit.Assert.assertEquals
import org.junit.Test

class RideNotificationQueueTest {
    private class Fixture {
        var now = 0L
        var scheduled: Pair<Long, () -> Unit>? = null
        val shown = mutableListOf<Triple<String, Long, Long>>()
        val queue = RideNotificationQueue<String>(
            show = { value, duration -> shown += Triple(value, now, duration) },
            schedule = { delay, action -> scheduled = (now + delay) to action },
            cancelScheduled = { scheduled = null },
        )

        fun advanceTo(time: Long) {
            while (scheduled?.first?.let { it <= time } == true) {
                val (at, action) = checkNotNull(scheduled)
                scheduled = null
                now = at
                action()
            }
            now = time
        }
    }

    @Test
    fun `burst alerts including repeated IDs each get seven seconds`() {
        val f = Fixture()
        f.queue.enqueue("battery")
        f.advanceTo(1_000)
        f.queue.enqueue("storage")
        f.queue.enqueue("battery")
        f.advanceTo(6_999)
        assertEquals(listOf(Triple("battery", 0L, 7_000L)), f.shown)
        f.advanceTo(14_000)
        assertEquals(listOf(
            Triple("battery", 0L, 7_000L),
            Triple("storage", 7_000L, 7_000L),
            Triple("battery", 14_000L, 7_000L),
        ), f.shown)
    }

    @Test
    fun `short requested duration is raised and longer duration delays the next alert`() {
        val f = Fixture()
        f.queue.enqueue("short", 2_000)
        f.queue.enqueue("long", 12_000)
        f.queue.enqueue("next")
        f.advanceTo(18_999)
        assertEquals(listOf(Triple("short", 0L, 7_000L), Triple("long", 7_000L, 12_000L)), f.shown)
        f.advanceTo(19_000)
        assertEquals(Triple("next", 19_000L, 7_000L), f.shown.last())
    }

    @Test
    fun `pause discards waiting alerts without letting a quick resume replace the current alert early`() {
        val f = Fixture()
        f.queue.enqueue("visible")
        f.queue.enqueue("stale waiting alert")
        f.advanceTo(1_000)
        f.queue.clearPending()
        f.queue.enqueue("after resume")
        f.advanceTo(7_000)
        assertEquals(listOf(Triple("visible", 0L, 7_000L), Triple("after resume", 7_000L, 7_000L)), f.shown)
    }

    @Test
    fun `stop cancels pending work and an old callback cannot advance a restarted queue`() {
        val f = Fixture()
        f.queue.enqueue("before stop")
        f.queue.enqueue("stale")
        val oldCallback = checkNotNull(f.scheduled).second
        f.queue.clear()
        f.advanceTo(1_000)
        f.queue.enqueue("after restart")
        f.queue.enqueue("following")
        oldCallback()
        assertEquals(2, f.shown.size)
        f.advanceTo(8_000)
        assertEquals(Triple("following", 8_000L, 7_000L), f.shown.last())
    }
}
