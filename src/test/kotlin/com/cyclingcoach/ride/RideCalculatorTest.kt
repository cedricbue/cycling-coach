package com.cyclingcoach.ride

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class RideCalculatorTest {

    // ---- calculateTss ----

    @Test
    fun `calculateTss at FTP for 1 hour returns 100`() {
        val ftp = 250.0

        val tss = RideCalculator.calculateTss(3600.0, ftp, ftp)

        assertThat(tss).isCloseTo(100.0, Offset.offset(0.001))
    }

    @Test
    fun `calculateTss at 0_8 IF for 2 hours returns 128`() {
        val ftp = 250.0
        val np = ftp * 0.8

        val tss = RideCalculator.calculateTss(7200.0, np, ftp)

        assertThat(tss).isCloseTo(128.0, Offset.offset(0.001))
    }

    // ---- calculateVariabilityIndex ----

    @Test
    fun `calculateVariabilityIndex with equal NP and avgPower returns 1_0`() {
        assertThat(RideCalculator.calculateVariabilityIndex(200.0, 200.0)).isCloseTo(1.0, Offset.offset(0.001))
    }

    @Test
    fun `calculateVariabilityIndex returns null for zero avgPower`() {
        assertThat(RideCalculator.calculateVariabilityIndex(200.0, 0.0)).isNull()
    }

    // ---- calculateEfficiencyFactor ----

    @Test
    fun `calculateEfficiencyFactor returns null when avgHr is null`() {
        assertThat(RideCalculator.calculateEfficiencyFactor(200.0, null)).isNull()
    }

    @Test
    fun `calculateEfficiencyFactor returns null when avgHr is zero`() {
        assertThat(RideCalculator.calculateEfficiencyFactor(200.0, 0.0)).isNull()
    }

    @Test
    fun `calculateEfficiencyFactor divides NP by avgHr`() {
        val result = RideCalculator.calculateEfficiencyFactor(200.0, 150.0)

        assertThat(result).isNotNull().isCloseTo(200.0 / 150.0, Offset.offset(0.001))
    }
}

