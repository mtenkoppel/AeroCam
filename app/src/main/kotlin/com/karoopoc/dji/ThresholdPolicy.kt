package com.karoopoc.dji

/** Keep the existing noise margin, but allow small positive thresholds to rearm. */
object ThresholdPolicy {
    fun remainsAbove(value: Double, threshold: Double, margin: Double): Boolean =
        value >= threshold - minOf(margin, threshold / 2.0)
}
