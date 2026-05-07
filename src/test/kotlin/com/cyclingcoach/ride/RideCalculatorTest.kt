package com.cyclingcoach.ride

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant

@Tag("unit")
class RideCalculatorTest {

    // ---- expandToDenseStream ----

    @Test
    fun `expandToDenseStream returns empty list when input is empty`() {
        assertThat(RideCalculator.expandToDenseStream(emptyList(), emptyList())).isEmpty()
    }

    @Test
    fun `expandToDenseStream produces one sample per second for 1s intervals`() {
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val values = listOf(100, 110, 120)
        val timestamps = listOf(t0, t0.plusSeconds(1), t0.plusSeconds(2))

        val result = RideCalculator.expandToDenseStream(values, timestamps)

        assertThat(result).containsExactly(100, 110, 120)
    }

    @Test
    fun `expandToDenseStream forward-fills gaps within maxGapSeconds`() {
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val values = listOf(100, 200)
        val timestamps = listOf(t0, t0.plusSeconds(3))

        val result = RideCalculator.expandToDenseStream(values, timestamps)

        // 1 sample at 100, then 2 forward-filled 100s, then 200
        assertThat(result).containsExactly(100, 100, 100, 200)
    }

    @Test
    fun `expandToDenseStream fills large gaps with zeros`() {
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val values = listOf(100, 200)
        val timestamps = listOf(t0, t0.plusSeconds(8))

        val result = RideCalculator.expandToDenseStream(values, timestamps, maxGapSeconds = 5)

        // gap = 8s > 5s → fill 7 zeros (seconds 1-7), then 200 at second 8 → 9 total
        assertThat(result).hasSize(9)
        assertThat(result.first()).isEqualTo(100)
        assertThat(result.subList(1, 8)).allMatch { it == 0 }
        assertThat(result.last()).isEqualTo(200)
    }

    @Test
    fun `expandToDenseStream handles null values by using last known value`() {
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val values = listOf(150, null, 200)
        val timestamps = listOf(t0, t0.plusSeconds(1), t0.plusSeconds(2))

        val result = RideCalculator.expandToDenseStream(values, timestamps)

        assertThat(result).containsExactly(150, 150, 200)
    }

    // ---- calculateNormalizedPower ----

    @Test
    fun `calculateNormalizedPower returns null when stream has fewer than 30 samples`() {
        val stream = List(29) { 200 }

        assertThat(RideCalculator.calculateNormalizedPower(stream)).isNull()
    }

    @Test
    fun `calculateNormalizedPower with constant power returns that power`() {
        val stream = List(300) { 200 }

        val result = RideCalculator.calculateNormalizedPower(stream)

        assertThat(result).isNotNull().isCloseTo(200.0, Offset.offset(0.01))
    }

    @Test
    fun `calculateNormalizedPower with alternating 100W and 300W returns 200W`() {
        // Each 30s rolling window contains exactly 15×100W + 15×300W = 200W average.
        // NP of all-200W windows = 200W.
        val stream = (1..300).map { if (it % 2 == 0) 100 else 300 }

        val result = RideCalculator.calculateNormalizedPower(stream)

        assertThat(result).isNotNull().isCloseTo(200.0, Offset.offset(0.5))
    }

    // ---- calculateBestPower ----

    @Test
    fun `calculateBestPower returns null when stream is shorter than window`() {
        val stream = List(4) { 200 }

        assertThat(RideCalculator.calculateBestPower(stream, 5)).isNull()
    }

    @Test
    fun `calculateBestPower finds peak window`() {
        // 10 samples: first 5 at 100W, last 5 at 300W
        val stream = List(5) { 100 } + List(5) { 300 }

        val result = RideCalculator.calculateBestPower(stream, 5)

        assertThat(result).isCloseTo(300.0, Offset.offset(0.01))
    }

    @Test
    fun `calculateBestPower returns exact mean for equal stream`() {
        val stream = List(60) { 250 }

        assertThat(RideCalculator.calculateBestPower(stream, 60)).isCloseTo(250.0, Offset.offset(0.01))
    }

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

    // ---- calculateElevationGain / Descent ----

    @Test
    fun `calculateElevationGain returns 0 for flat profile`() {
        assertThat(RideCalculator.calculateElevationGain(listOf(100.0, 100.0, 100.0))).isEqualTo(0.0)
    }

    @Test
    fun `calculateElevationGain sums only positive differences`() {
        val altitudes = listOf(100.0, 110.0, 105.0, 120.0)

        val gain = RideCalculator.calculateElevationGain(altitudes)

        assertThat(gain).isCloseTo(25.0, Offset.offset(0.001)) // +10, +0, +15
    }

    @Test
    fun `calculateElevationDescent sums only negative differences`() {
        val altitudes = listOf(120.0, 110.0, 115.0, 100.0)

        val descent = RideCalculator.calculateElevationDescent(altitudes)

        assertThat(descent).isCloseTo(25.0, Offset.offset(0.001)) // -10, +0, -15
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
