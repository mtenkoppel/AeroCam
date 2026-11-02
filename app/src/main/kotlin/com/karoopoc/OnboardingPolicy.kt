package com.karoopoc

/** Pure onboarding choices; the transport and keep-awake scheduler stay unchanged. */
object OnboardingPolicy {
    fun intervalSeconds(standbySeconds: Int): Int {
        require(standbySeconds in listOf(60, 120, 300))
        return standbySeconds - 30
    }
    fun threshold(text: String): Int? = text.toIntOrNull()?.takeIf { it > 0 }
    fun powerMessage(value: Int): Int = when {
        value > 500 -> 3
        value >= 350 -> 2
        value >= 300 -> 1
        else -> 0
    }
    fun speedMessage(value: Int): Int = when {
        value > 60 -> 3
        value > 55 -> 2
        value >= 50 -> 1
        else -> 0
    }
}
