package com.cyclingcoach.ride

import org.springframework.stereotype.Component
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Instant
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

@Component
class TcxParser : ActivityFileParser {

    private val dbFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    private val xpathFactory = XPathFactory.newInstance()

    private val ns = object : NamespaceContext {
        private val map = mapOf(
            "tcx" to "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2",
            "ns3" to "http://www.garmin.com/xmlschemas/ActivityExtension/v2",
        )

        override fun getNamespaceURI(prefix: String) = map[prefix] ?: ""
        override fun getPrefix(namespaceURI: String) = map.entries.find { it.value == namespaceURI }?.key
        override fun getPrefixes(namespaceURI: String) =
            map.entries.filter { it.value == namespaceURI }.map { it.key }.iterator()
    }

    override fun supports(content: String) = content.contains("TrainingCenterDatabase")

    override fun parse(content: String): RideData {
        val doc = dbFactory.newDocumentBuilder().parse(InputSource(StringReader(content)))
        val xpath = xpathFactory.newXPath().apply { namespaceContext = ns }

        fun evalString(expr: String, ctx: Any = doc): String =
            (xpath.evaluate(expr, ctx, XPathConstants.STRING) as String).trim()

        fun evalDouble(expr: String, ctx: Any = doc): Double? =
            evalString(expr, ctx).toDoubleOrNull()

        fun evalInt(expr: String, ctx: Any = doc): Int? =
            evalString(expr, ctx).toIntOrNull()

        fun evalNodes(expr: String, ctx: Any = doc): NodeList =
            xpath.evaluate(expr, ctx, XPathConstants.NODESET) as NodeList

        val durationSeconds = evalDouble("//tcx:TotalTimeSeconds") ?: 0.0
        val totalDistance = evalDouble("(//tcx:Lap/tcx:DistanceMeters)[1]") ?: 0.0
        val maxSpeed = evalDouble("(//tcx:Lap/tcx:MaximumSpeed)[1]")

        val trackpoints = evalNodes("//tcx:Trackpoint")
        val count = trackpoints.length

        val timestamps = ArrayList<Instant>(count)
        val powerWatts = ArrayList<Int?>(count)
        val heartRateBpm = ArrayList<Int?>(count)
        val cadenceRpm = ArrayList<Int?>(count)
        val speedMps = ArrayList<Double?>(count)
        val altitudeMeters = ArrayList<Double?>(count)
        val distanceMeters = ArrayList<Double?>(count)

        for (i in 0 until count) {
            val tp = trackpoints.item(i)
            val timeStr = evalString("tcx:Time", tp)
            if (timeStr.isEmpty()) continue
            timestamps.add(Instant.parse(timeStr))
            heartRateBpm.add(evalInt("tcx:HeartRateBpm/tcx:Value", tp))
            cadenceRpm.add(evalInt("tcx:Cadence", tp))
            altitudeMeters.add(evalDouble("tcx:AltitudeMeters", tp))
            distanceMeters.add(evalDouble("tcx:DistanceMeters", tp))
            speedMps.add(evalDouble("tcx:Extensions/ns3:TPX/ns3:Speed", tp))
            powerWatts.add(evalInt("tcx:Extensions/ns3:TPX/ns3:Watts", tp))
        }

        return RideData(
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
