package com.karoopoc.dji

import org.junit.Assert.*
import org.junit.Test

class GradientSpeedTriggerTest {
    @Test fun `uphill requires both its own speed and positive gradient`() {
        val trigger = GradientSpeedTrigger()
        assertFalse(trigger.update(19.0, 8.0, true, 20, 5))
        assertFalse(trigger.update(30.0, 4.9, true, 20, 5))
        assertFalse(trigger.update(30.0, -8.0, true, 20, 5))
        assertTrue(trigger.update(20.0, 5.0, true, 20, 5))
    }

    @Test fun `downhill requires both its own speed and negative gradient`() {
        val trigger = GradientSpeedTrigger()
        assertFalse(trigger.update(59.0, -10.0, true, 60, -6))
        assertFalse(trigger.update(70.0, -5.9, true, 60, -6))
        assertFalse(trigger.update(70.0, 10.0, true, 60, -6))
        assertTrue(trigger.update(60.0, -6.0, true, 60, -6))
    }

    @Test fun `directions can be enabled independently and reset when disabled`() {
        val uphill = GradientSpeedTrigger()
        val downhill = GradientSpeedTrigger()
        assertFalse(uphill.update(25.0, 5.0, false, 20, 4))
        assertTrue(downhill.update(65.0, -7.0, true, 60, -6))
        assertTrue(uphill.update(25.0, 5.0, true, 20, 4))
        assertFalse(downhill.update(65.0, -7.0, false, 60, -6))
        assertTrue(downhill.update(65.0, -7.0, true, 60, -6))
    }

    @Test fun `hysteresis prevents repeated markers until descent is left`() {
        val trigger = GradientSpeedTrigger()
        assertTrue(trigger.update(60.0, -6.0, true, 60, -6))
        assertFalse(trigger.update(59.0, -5.5, true, 60, -6))
        assertFalse(trigger.update(61.0, -6.5, true, 60, -6))
        assertFalse(trigger.update(56.9, -7.0, true, 60, -6))
        assertTrue(trigger.update(60.0, -6.0, true, 60, -6))
        assertFalse(trigger.update(65.0, -4.9, true, 60, -6))
        assertTrue(trigger.update(65.0, -6.0, true, 60, -6))
    }

    @Test fun `stopping or flattening rearms low uphill thresholds`() {
        val trigger = GradientSpeedTrigger()
        assertTrue(trigger.update(1.0, 1.0, true, 1, 1))
        assertFalse(trigger.update(0.0, 1.0, true, 1, 1))
        assertTrue(trigger.update(1.0, 1.0, true, 1, 1))
        assertFalse(trigger.update(1.0, 0.0, true, 1, 1))
        assertTrue(trigger.update(1.0, 1.0, true, 1, 1))
    }

    @Test fun `missing or invalid sensor values do not trigger`() {
        val trigger = GradientSpeedTrigger()
        assertFalse(trigger.update(null, -6.0, true, 60, -6))
        assertFalse(trigger.update(60.0, null, true, 60, -6))
        assertFalse(trigger.update(Double.NaN, -6.0, true, 60, -6))
        assertFalse(trigger.update(60.0, Double.NEGATIVE_INFINITY, true, 60, -6))
        assertFalse(trigger.update(60.0, -6.0, true, 60, 0))
    }
}
