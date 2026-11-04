package com.karoopoc.dji

/** One direction’s gradient trigger with an optional speed condition, with hysteresis to avoid repeated markers. */
internal class GradientSpeedTrigger {
    private var active = false

    fun update(speedKph: Double?, gradePercent: Double?, enabled: Boolean,
        speedThresholdKph: Int, gradientThresholdPercent: Int, requireSpeed: Boolean = true): Boolean {
        if (!enabled || gradientThresholdPercent == 0) {
            reset()
            return false
        }
        if (gradePercent == null || !gradePercent.isFinite()) return false
        if (requireSpeed && (speedKph == null || !speedKph.isFinite())) return false
        val uphill = gradientThresholdPercent > 0
        val meetsGrade = if (uphill) gradePercent >= gradientThresholdPercent else gradePercent <= gradientThresholdPercent
        val triggered = !active && (!requireSpeed || speedKph!! >= speedThresholdKph) && meetsGrade
        active = if (active) {
            // Retain the original downhill margins: 3 km/h and 1 percentage point.
            val gradeMargin = minOf(1.0, kotlin.math.abs(gradientThresholdPercent) / 2.0)
            val releaseGrade = if (uphill) gradientThresholdPercent - gradeMargin else gradientThresholdPercent + gradeMargin
            (!requireSpeed || speedKph!! >= maxOf(1, speedThresholdKph - 3)) &&
                (if (uphill) gradePercent >= releaseGrade else gradePercent <= releaseGrade)
        } else triggered
        return triggered
    }

    fun reset() { active = false }
}
