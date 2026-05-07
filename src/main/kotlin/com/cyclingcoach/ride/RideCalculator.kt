package com.cyclingcoach.ride

import java.time.Instant

object RideCalculator {

    fun expandToDenseStream(
        values: List<Int?>,
        timestamps: List<Instant>,
        maxGapSeconds: Int = 5,
    ): List<Int> {
        if (values.isEmpty() || timestamps.isEmpty()) return emptyList()
        val dense = ArrayList<Int>(values.size)
        var lastValue = 0
        for (i in values.indices) {
            val current = values[i] ?: lastValue
            if (i == 0) {
                dense.add(current)
                lastValue = current
                continue
            }
            val gapSeconds = (timestamps[i].epochSecond - timestamps[i - 1].epochSecond).coerceAtLeast(1)
            val fillValue = if (gapSeconds <= maxGapSeconds) lastValue else 0
            repeat((gapSeconds - 1).toInt()) { dense.add(fillValue) }
            dense.add(current)
            lastValue = current
        }
        return dense
    }

    fun calculateNormalizedPower(densePowerStream: List<Int>): Double? {
        if (densePowerStream.size < 30) return null
        val rolling = (0..densePowerStream.size - 30).map { i ->
            densePowerStream.subList(i, i + 30).average()
        }
        val meanFourthPower = rolling.map { it * it * it * it }.average()
        return Math.pow(meanFourthPower, 0.25)
    }

    fun calculateBestPower(densePowerStream: List<Int>, windowSeconds: Int): Double? {
        if (densePowerStream.size < windowSeconds) return null
        var windowSum = densePowerStream.take(windowSeconds).sum().toLong()
        var best = windowSum
        for (i in windowSeconds until densePowerStream.size) {
            windowSum += densePowerStream[i] - densePowerStream[i - windowSeconds]
            if (windowSum > best) best = windowSum
        }
        return best.toDouble() / windowSeconds
    }

    fun calculateIntensityFactor(np: Double, ftp: Double): Double = np / ftp

    fun calculateTss(durationSeconds: Double, np: Double, ftp: Double): Double {
        val intensityFactor = np / ftp
        return (durationSeconds * np * intensityFactor) / (ftp * 3600.0) * 100.0
    }

    fun calculateVariabilityIndex(np: Double, avgPower: Double): Double? {
        if (avgPower <= 0) return null
        return np / avgPower
    }

    fun calculateEfficiencyFactor(np: Double, avgHr: Double?): Double? {
        if (avgHr == null || avgHr <= 0) return null
        return np / avgHr
    }

    fun calculateElevationGain(altitudes: List<Double>): Double =
        altitudes.zipWithNext().sumOf { (a, b) -> if (b > a) b - a else 0.0 }

    fun calculateElevationDescent(altitudes: List<Double>): Double =
        altitudes.zipWithNext().sumOf { (a, b) -> if (b < a) a - b else 0.0 }

    fun calculateAvgGrade(altitudes: List<Double>, distances: List<Double>): Double? {
        if (altitudes.size < 2 || distances.size < 2) return null
        val totalDist = distances.last() - distances.first()
        if (totalDist <= 0) return null
        return (altitudes.last() - altitudes.first()) / totalDist * 100.0
    }

    fun calculateMaxGrade(altitudes: List<Double>, distances: List<Double>): Double? {
        if (altitudes.size < 2 || distances.size < 2) return null
        return altitudes.zip(distances).zipWithNext().mapNotNull { (prev, curr) ->
            val distDelta = curr.second - prev.second
            if (distDelta <= 0) null else (curr.first - prev.first) / distDelta * 100.0
        }.maxOrNull()
    }
}
