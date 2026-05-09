package com.zipper.datingapp.ui

import com.zipper.datingapp.data.computeHostLevel
import com.zipper.datingapp.economy.VirtualEconomyMath
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure math coverage for host level scaling (non-binary / legacy path uses [computeHostLevel], capped at [VirtualEconomyMath.MAX_APP_LEVEL]).
 */
class DatingViewModelTest {

    @Test
    fun computeHostLevel_zeroInputs_isOne() {
        assertEquals(1, computeHostLevel(0, 0))
    }

    @Test
    fun computeHostLevel_fiveHoursStreaming_incrementsFromTime() {
        val minutes = 5 * 60L
        assertEquals(2, computeHostLevel(minutes, 0))
    }

    @Test
    fun computeHostLevel_diamondsIncrement() {
        assertEquals(2, computeHostLevel(0, 500))
        assertEquals(3, computeHostLevel(0, 1000))
    }

    @Test
    fun computeHostLevel_combinesStreamsAndDiamonds() {
        val minutes = 5 * 60L
        assertEquals(3, computeHostLevel(minutes, 500))
    }

    @Test
    fun computeHostLevel_capsAtMaxAppLevel() {
        assertEquals(11, computeHostLevel(300_000L, 500_000))
        assertEquals(11, computeHostLevel(Long.MAX_VALUE / 4, Int.MAX_VALUE))
    }

    @Test
    fun femaleLevelFromBeans_reaches4At500k() {
        assertEquals(4, VirtualEconomyMath.femaleLevelFromBeans(500_000L))
        assertEquals(3, VirtualEconomyMath.femaleLevelFromBeans(499_999L))
    }

    @Test
    fun maleGiverLevel_reaches4At1MSpend() {
        assertEquals(4, VirtualEconomyMath.maleGiverLevelFromGiftSpend(1_000_000L))
        assertEquals(3, VirtualEconomyMath.maleGiverLevelFromGiftSpend(999_999L))
    }

    @Test
    fun computeHostLevel_negativeInputs_clamped() {
        assertEquals(1, computeHostLevel(-100L, -50))
    }
}
