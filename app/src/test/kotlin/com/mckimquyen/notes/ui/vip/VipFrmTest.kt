package com.mckimquyen.notes.ui.vip

import org.junit.Assert.assertEquals
import org.junit.Test

class VipFrmTest {

    private val oneDayMs = 24L * 60L * 60L * 1000L

    @Test
    fun `no existing VIP time adds just the reward days`() {
        val now = 1_000_000L
        val total = computeVipRewardTotalDays(
            currentExpiryMs = 0L, nowMs = now, rewardDays = 3, maxDays = 365,
        )
        assertEquals(3, total)
    }

    @Test
    fun `remaining time accumulates on top of the reward`() {
        val now = 1_000_000L
        val currentExpiry = now + 10 * oneDayMs
        val total = computeVipRewardTotalDays(
            currentExpiryMs = currentExpiry, nowMs = now, rewardDays = 3, maxDays = 365,
        )
        assertEquals(13, total)
    }

    @Test
    fun `clamps to maxDays instead of exceeding the SDK's activateVipByKey cap`() {
        val now = 1_000_000L
        // 363 days remaining + 3 reward days would be 366, over the 365 cap.
        val currentExpiry = now + 363 * oneDayMs
        val total = computeVipRewardTotalDays(
            currentExpiryMs = currentExpiry, nowMs = now, rewardDays = 3, maxDays = 365,
        )
        assertEquals(365, total)
    }

    @Test
    fun `already past the cap still returns exactly maxDays, not more`() {
        val now = 1_000_000L
        val currentExpiry = now + 400 * oneDayMs
        val total = computeVipRewardTotalDays(
            currentExpiryMs = currentExpiry, nowMs = now, rewardDays = 3, maxDays = 365,
        )
        assertEquals(365, total)
    }

    @Test
    fun `expired VIP (expiry in the past) is treated as zero remaining`() {
        val now = 1_000_000L
        val expiredExpiry = now - 10 * oneDayMs
        val total = computeVipRewardTotalDays(
            currentExpiryMs = expiredExpiry, nowMs = now, rewardDays = 3, maxDays = 365,
        )
        assertEquals(3, total)
    }
}
