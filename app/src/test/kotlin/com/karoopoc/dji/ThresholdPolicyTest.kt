package com.karoopoc.dji

import org.junit.Assert.*
import org.junit.Test

class ThresholdPolicyTest {
    @Test fun normalThresholdsKeepTheirExistingMargins() {
        assertTrue(ThresholdPolicy.remainsAbove(200.0, 250.0, 50.0))
        assertFalse(ThresholdPolicy.remainsAbove(199.0, 250.0, 50.0))
        assertTrue(ThresholdPolicy.remainsAbove(37.0, 40.0, 3.0))
        assertFalse(ThresholdPolicy.remainsAbove(36.9, 40.0, 3.0))
        assertTrue(ThresholdPolicy.remainsAbove(162.0, 165.0, 3.0))
    }
    @Test fun smallThresholdCanRearmWhenMeasurementFallsToZero() {
        assertFalse(ThresholdPolicy.remainsAbove(0.0, 1.0, 50.0))
        assertFalse(ThresholdPolicy.remainsAbove(0.0, 1.0, 3.0))
        assertTrue(ThresholdPolicy.remainsAbove(0.5, 1.0, 3.0))
    }
}
