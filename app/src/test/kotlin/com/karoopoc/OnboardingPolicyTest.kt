package com.karoopoc

import org.junit.Assert.*
import org.junit.Test

class OnboardingPolicyTest {
    @Test fun cameraTimerLeavesThirtySecondsForRetries() {
        assertEquals(30, OnboardingPolicy.intervalSeconds(60))
        assertEquals(90, OnboardingPolicy.intervalSeconds(120))
        assertEquals(270, OnboardingPolicy.intervalSeconds(300))
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedCameraTimerCannotProduceUnsafeInterval() { OnboardingPolicy.intervalSeconds(30) }
    @Test fun thresholdsAcceptValuesOutsideOldBoundsButRejectInvalidInput() {
        listOf("250", "1200", "20", "110").forEach { assertEquals(it.toInt(), OnboardingPolicy.threshold(it)) }
        listOf("", "0", "-2", "12.5", "hello", "999999999999999999").forEach { assertNull(OnboardingPolicy.threshold(it)) }
    }
    @Test fun gradientInputsUseTheSameSignedRangesAsSettings() {
        assertEquals(18, OnboardingPolicy.gradientSpeed("18", true))
        assertEquals(65, OnboardingPolicy.gradientSpeed("65", false))
        assertEquals(5, OnboardingPolicy.gradient("+5", true))
        assertEquals(-8, OnboardingPolicy.gradient("-8", false))
        listOf("", "-", "0", "-3", "31", "1.5", "999999999999999999").forEach {
            assertNull(OnboardingPolicy.gradient(it, true))
        }
        listOf("", "-", "0", "3", "-31", "-1.5").forEach {
            assertNull(OnboardingPolicy.gradient(it, false))
        }
        assertNull(OnboardingPolicy.gradientSpeed("39", false))
        assertNull(OnboardingPolicy.gradientSpeed("81", true))
        assertNull(OnboardingPolicy.gradientSpeed("0", true))
    }
    @Test fun playfulFeedbackUsesAgreedBoundaries() {
        assertEquals(0, OnboardingPolicy.powerMessage(250))
        assertEquals(1, OnboardingPolicy.powerMessage(300))
        assertEquals(2, OnboardingPolicy.powerMessage(350))
        assertEquals(2, OnboardingPolicy.powerMessage(500))
        assertEquals(3, OnboardingPolicy.powerMessage(501))
        assertEquals(0, OnboardingPolicy.speedMessage(40))
        assertEquals(1, OnboardingPolicy.speedMessage(50))
        assertEquals(1, OnboardingPolicy.speedMessage(55))
        assertEquals(2, OnboardingPolicy.speedMessage(56))
        assertEquals(2, OnboardingPolicy.speedMessage(60))
        assertEquals(3, OnboardingPolicy.speedMessage(61))
    }
}
