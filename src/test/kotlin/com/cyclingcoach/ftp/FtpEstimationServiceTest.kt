package com.cyclingcoach.ftp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class FtpEstimationServiceTest {

    private val service = FtpEstimationService()

    // --- no data ---

    @Test
    fun `returns null when no eligible rides exist`() {
        assertThat(service.estimate(emptyList())).isNull()
    }

    @Test
    fun `returns null when rides exist but have no qualifying power fields`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 3600.0, bestPower5min = null, bestPower10min = null, bestPower20min = null, bestPower60min = null),
        )
        assertThat(service.estimate(samples)).isNull()
    }

    @Test
    fun `returns null when only zero-watt power fields are present`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 3600.0, bestPower5min = 0.0, bestPower10min = 0.0, bestPower20min = 0.0, bestPower60min = 0.0),
        )
        assertThat(service.estimate(samples)).isNull()
    }

    // --- single-point fallbacks ---

    @Test
    fun `estimates from 60min best (direct, highest trust)`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 3600.0, bestPower5min = null, bestPower10min = null, bestPower20min = null, bestPower60min = 300.0),
        )
        // 60min × 1.00 = 300W; only one estimate so result = 300
        assertThat(service.estimate(samples)).isEqualTo(300.0)
    }

    @Test
    fun `estimates from 20min best using Coggan 0_95 factor`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 1200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 300.0, bestPower60min = null),
        )
        // 300 × 0.95 = 285W
        assertThat(service.estimate(samples)).isEqualTo(285.0)
    }

    @Test
    fun `estimates from 10min best using 0_85 factor`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 600.0, bestPower5min = null, bestPower10min = 280.0, bestPower20min = null, bestPower60min = null),
        )
        // 280 × 0.85 = 238W
        assertThat(service.estimate(samples)).isEqualTo(238.0)
    }

    // --- duration guards ---

    @Test
    fun `does not use 20min best from a ride shorter than 20 minutes`() {
        // Ride is only 15 min — 20min field must be ignored → no qualifying fields → null
        val samples = listOf(
            RidePowerSample(durationSeconds = 900.0, bestPower5min = null, bestPower10min = null, bestPower20min = 400.0, bestPower60min = null),
        )
        assertThat(service.estimate(samples)).isNull()
    }

    @Test
    fun `does not use 60min best from a ride shorter than 60 minutes`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 2400.0, bestPower5min = null, bestPower10min = null, bestPower20min = null, bestPower60min = 350.0),
        )
        assertThat(service.estimate(samples)).isNull()
    }

    // --- MAX across rides ---

    @Test
    fun `takes the maximum best power across multiple rides`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 1200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 280.0, bestPower60min = null),
            RidePowerSample(durationSeconds = 1200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 310.0, bestPower60min = null),
            RidePowerSample(durationSeconds = 1200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 295.0, bestPower60min = null),
        )
        // max 20min = 310 × 0.95 = 294.5 → 295
        assertThat(service.estimate(samples)).isCloseTo(295.0, org.assertj.core.data.Offset.offset(1.0))
    }

    // --- CP model ---

    @Test
    fun `CP model is used when multiple duration points are available`() {
        // 20min = 280W, 60min = 240W → CP model fits line through two work values
        val samples = listOf(
            RidePowerSample(durationSeconds = 7200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 280.0, bestPower60min = 240.0),
        )
        val result = service.estimate(samples)
        assertThat(result).isNotNull
        assertThat(result!!).isBetween(220.0, 280.0)
    }

    @Test
    fun `CP model is discarded when it produces implausible W-prime (negative)`() {
        // 60min > 20min is physically impossible — CP model yields negative W', so it is discarded.
        val samples = listOf(
            RidePowerSample(durationSeconds = 7200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 200.0, bestPower60min = 260.0),
        )
        val result = service.estimate(samples)
        // CP model dropped; single-point fallbacks remain (60min=260, 20min×0.95=190)
        assertThat(result).isNotNull
        assertThat(result!!).isBetween(190.0, 270.0)
    }

    // --- weighted combination ---

    @Test
    fun `result is a weighted average when multiple estimates are available`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 7200.0, bestPower5min = null, bestPower10min = null, bestPower20min = 280.0, bestPower60min = 250.0),
        )
        val result = service.estimate(samples)
        assertThat(result).isNotNull
        assertThat(result!!).isBetween(245.0, 280.0)
    }

    // --- edge cases ---

    @Test
    fun `implausibly low FTP estimate from 10min yields a result without crashing`() {
        val samples = listOf(
            RidePowerSample(durationSeconds = 600.0, bestPower5min = null, bestPower10min = 5.0, bestPower20min = null, bestPower60min = null),
        )
        val result = service.estimate(samples)
        assertThat(result).isNotNull
        assertThat(result!!).isGreaterThan(0.0)
    }
}

