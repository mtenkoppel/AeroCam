package com.karoopoc.dji

import org.junit.Assert.*
import org.junit.Test

class HighlightCooldownTest {
    @Test fun firstHighlightIsAllowedEvenJustAfterBoot() {
        assertTrue(HighlightCooldown().allows(0, 30))
    }

    @Test fun simultaneousTriggersAndEarlyRetriggersAreSuppressed() {
        val cooldown = HighlightCooldown()
        cooldown.onSent(1_000)
        assertFalse(cooldown.allows(1_000, 30))
        assertFalse(cooldown.allows(30_999, 30))
        assertTrue(cooldown.allows(31_000, 30))
    }

    @Test fun skippedOrFailedRequestsDoNotExtendCooldown() {
        val cooldown = HighlightCooldown()
        // Requests which fail never call onSent.
        assertTrue(cooldown.allows(0, 30))
        assertTrue(cooldown.allows(1, 30))
        cooldown.onSent(1_000)
        assertFalse(cooldown.allows(20_000, 30))
        assertTrue(cooldown.allows(31_000, 30))
    }

    @Test fun exemptionsAreAllowedAndTheirSuccessfulSendsSpaceThresholds() {
        val cooldown = HighlightCooldown()
        cooldown.onSent(1_000)
        assertTrue(cooldown.allows(2_000, 30, exempt = true))
        cooldown.onSent(2_000)
        assertFalse(cooldown.allows(31_000, 30))
        assertTrue(cooldown.allows(32_000, 30))
    }

    @Test fun changesTakeEffectWithoutResettingLastSend() {
        val cooldown = HighlightCooldown()
        cooldown.onSent(1_000)
        assertTrue(cooldown.allows(1_000, 0))
        assertTrue(cooldown.allows(16_000, 15))
        assertFalse(cooldown.allows(16_000, 30))
        assertFalse(cooldown.allows(60_999, 60))
        assertTrue(cooldown.allows(61_000, 60))
    }

    @Test fun onlySupportedOptionsAreAccepted() {
        assertEquals(30, HighlightCooldown.DEFAULT_SECONDS)
        for (option in listOf(0, 15, 30, 60)) assertEquals(option, HighlightCooldown.normalize(option))
        for (invalid in listOf(-1, 1, 20, 120)) assertEquals(30, HighlightCooldown.normalize(invalid))
    }
}
