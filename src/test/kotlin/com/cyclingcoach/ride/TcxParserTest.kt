package com.cyclingcoach.ride

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class TcxParserTest {

    private val parser = TcxParser()

    private fun loadFixture(name: String): String =
        TcxParserTest::class.java.getResourceAsStream("/fixtures/garmin/$name")!!
            .bufferedReader().readText()

    @Test
    fun `supports returns true for TCX content`() {
        assertThat(parser.supports("<TrainingCenterDatabase>")).isTrue()
    }

    @Test
    fun `supports returns false for non-TCX content`() {
        assertThat(parser.supports("not xml at all")).isFalse()
    }

    @Test
    fun `parse fixture produces equal-length parallel streams`() {
        val tcx = loadFixture("activity_22801381040.tcx")

        val rideData = parser.parse(tcx)

        val size = rideData.timestamps.size
        assertThat(size).isGreaterThan(0)
        assertThat(rideData.powerWatts).hasSize(size)
        assertThat(rideData.heartRateBpm).hasSize(size)
        assertThat(rideData.cadenceRpm).hasSize(size)
        assertThat(rideData.speedMps).hasSize(size)
        assertThat(rideData.altitudeMeters).hasSize(size)
        assertThat(rideData.distanceMeters).hasSize(size)
    }

    @Test
    fun `parse fixture extracts correct duration`() {
        val rideData = parser.parse(loadFixture("activity_22801381040.tcx"))

        assertThat(rideData.durationSeconds).isCloseTo(3634.0, Offset.offset(1.0))
    }

    @Test
    fun `parse fixture extracts correct total distance`() {
        val rideData = parser.parse(loadFixture("activity_22801381040.tcx"))

        assertThat(rideData.totalDistanceMeters).isCloseTo(29812.77, Offset.offset(1.0))
    }

    @Test
    fun `parse fixture extracts max speed`() {
        val rideData = parser.parse(loadFixture("activity_22801381040.tcx"))

        assertThat(rideData.maxSpeedMps).isNotNull().isCloseTo(10.41, Offset.offset(0.1))
    }

    @Test
    fun `parse fixture produces power values within valid range`() {
        val rideData = parser.parse(loadFixture("activity_22801381040.tcx"))

        val nonNullPower = rideData.powerWatts.filterNotNull()
        assertThat(nonNullPower).isNotEmpty()
        assertThat(nonNullPower.all { it in 0..2000 }).isTrue()
    }

    @Test
    fun `parse minimal TCX without trackpoints returns empty streams`() {
        val minimalTcx = """<?xml version="1.0"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Biking">
                  <Id>2026-01-01T00:00:00.000Z</Id>
                  <Lap StartTime="2026-01-01T00:00:00.000Z">
                    <TotalTimeSeconds>0.0</TotalTimeSeconds>
                    <DistanceMeters>0.0</DistanceMeters>
                    <Track/>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>"""

        val rideData = parser.parse(minimalTcx)

        assertThat(rideData.timestamps).isEmpty()
        assertThat(rideData.powerWatts).isEmpty()
        assertThat(rideData.durationSeconds).isCloseTo(0.0, Offset.offset(0.001))
    }

    @Test
    fun `parse TCX without Extensions block produces empty power stream`() {
        val tcxNoExtensions = """<?xml version="1.0"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Biking">
                  <Id>2026-01-01T00:00:00.000Z</Id>
                  <Lap StartTime="2026-01-01T00:00:00.000Z">
                    <TotalTimeSeconds>60.0</TotalTimeSeconds>
                    <DistanceMeters>500.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-01-01T00:00:00.000Z</Time>
                        <AltitudeMeters>100.0</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>140</Value></HeartRateBpm>
                        <Cadence>90</Cadence>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>"""

        val rideData = parser.parse(tcxNoExtensions)

        assertThat(rideData.timestamps).hasSize(1)
        assertThat(rideData.powerWatts).containsExactly(null)
        assertThat(rideData.heartRateBpm).containsExactly(140)
    }
}
