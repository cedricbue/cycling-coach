package com.cyclingcoach.tcx

import java.io.StringReader
import java.time.Instant
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

class TcxParser {

    private val xmlInputFactory = XMLInputFactory.newInstance().apply {
        setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
        setProperty(XMLInputFactory.IS_COALESCING, true)
    }

    fun supports(content: String) = content.contains("TrainingCenterDatabase")

    fun parse(content: String): TcxData {
        val reader = xmlInputFactory.createXMLStreamReader(StringReader(content))

        var durationSeconds = 0.0
        var totalDistance = 0.0
        var maxSpeed: Double? = null

        val timestamps = ArrayList<Instant>()
        val powerWatts = ArrayList<Int?>()
        val heartRateBpm = ArrayList<Int?>()
        val cadenceRpm = ArrayList<Int?>()
        val speedMps = ArrayList<Double?>()
        val altitudeMeters = ArrayList<Double?>()
        val distanceMeters = ArrayList<Double?>()

        var firstLapSeen = false
        var firstLapDone = false
        var inTrack = false
        var inTrackpoint = false
        var inHeartRateBpm = false
        var inTPX = false
        var currentElement: String? = null

        var tpTime: Instant? = null
        var tpHR: Int? = null
        var tpCadence: Int? = null
        var tpAltitude: Double? = null
        var tpDistance: Double? = null
        var tpSpeed: Double? = null
        var tpPower: Int? = null

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    currentElement = reader.localName
                    when (currentElement) {
                        "Lap" -> if (!firstLapDone) firstLapSeen = true
                        "Track" -> if (firstLapSeen && !firstLapDone) inTrack = true
                        "Trackpoint" -> {
                            inTrackpoint = true
                            tpTime = null; tpHR = null; tpCadence = null
                            tpAltitude = null; tpDistance = null; tpSpeed = null; tpPower = null
                        }
                        "HeartRateBpm" -> if (inTrackpoint) inHeartRateBpm = true
                        "TPX" -> if (inTrackpoint) inTPX = true
                    }
                }
                XMLStreamConstants.END_ELEMENT -> {
                    when (reader.localName) {
                        "Lap" -> { firstLapSeen = false; firstLapDone = true }
                        "Track" -> inTrack = false
                        "Trackpoint" -> {
                            inTrackpoint = false
                            if (tpTime != null) {
                                timestamps.add(tpTime!!)
                                heartRateBpm.add(tpHR)
                                cadenceRpm.add(tpCadence)
                                altitudeMeters.add(tpAltitude)
                                distanceMeters.add(tpDistance)
                                speedMps.add(tpSpeed)
                                powerWatts.add(tpPower)
                            }
                        }
                        "HeartRateBpm" -> inHeartRateBpm = false
                        "TPX" -> inTPX = false
                    }
                    currentElement = null
                }
                XMLStreamConstants.CHARACTERS -> {
                    val text = reader.text.trim()
                    if (text.isEmpty()) continue
                    val el = currentElement ?: continue
                    when {
                        inTrackpoint -> when {
                            el == "Time" -> tpTime = runCatching { Instant.parse(text) }.getOrNull()
                            el == "Value" && inHeartRateBpm -> tpHR = text.toIntOrNull()
                            el == "Cadence" -> tpCadence = text.toIntOrNull()
                            el == "AltitudeMeters" -> tpAltitude = text.toDoubleOrNull()
                            el == "DistanceMeters" -> tpDistance = text.toDoubleOrNull()
                            el == "Speed" && inTPX -> tpSpeed = text.toDoubleOrNull()
                            el == "Watts" && inTPX -> tpPower = text.toIntOrNull()
                        }
                        firstLapSeen && !firstLapDone && !inTrack -> when (el) {
                            "TotalTimeSeconds" -> durationSeconds = text.toDoubleOrNull() ?: 0.0
                            "DistanceMeters" -> totalDistance = text.toDoubleOrNull() ?: 0.0
                            "MaximumSpeed" -> maxSpeed = text.toDoubleOrNull()
                        }
                    }
                }
            }
        }
        reader.close()

        return TcxData(
            timestamps = timestamps,
            powerWatts = powerWatts,
            heartRateBpm = heartRateBpm,
            cadenceRpm = cadenceRpm,
            speedMps = speedMps,
            altitudeMeters = altitudeMeters,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            totalDistanceMeters = totalDistance,
            maxSpeedMps = maxSpeed,
        )
    }
}
