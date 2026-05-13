package com.cyclingcoach.ride

object RideCalculator {
    fun calculateIntensityFactor(
        np: Double,
        ftp: Double,
    ): Double = np / ftp

    fun calculateTss(
        durationSeconds: Double,
        np: Double,
        ftp: Double,
    ): Double {
        val intensityFactor = np / ftp
        return (durationSeconds * np * intensityFactor) / (ftp * 3600.0) * 100.0
    }

    fun calculateVariabilityIndex(
        np: Double,
        avgPower: Double,
    ): Double? {
        if (avgPower <= 0) return null
        return np / avgPower
    }

    fun calculateEfficiencyFactor(
        np: Double,
        avgHr: Double?,
    ): Double? {
        if (avgHr == null || avgHr <= 0) return null
        return np / avgHr
    }
}
