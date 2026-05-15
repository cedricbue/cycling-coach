package com.cyclingcoach.weather

import java.time.LocalDate
import java.time.LocalTime

data class WeatherData(
    val date: LocalDate,
    val hourlyTemps: List<Double>,
    val precipProbs: List<Int>,
    val windSpeeds: List<Double>,
    val windGusts: List<Double>,
    val sunrise: LocalTime,
    val sunset: LocalTime,
) {
    val minTemp: Double get() = hourlyTemps.minOrNull() ?: Double.MAX_VALUE
    val maxTemp: Double get() = hourlyTemps.maxOrNull() ?: Double.MIN_VALUE
    val maxPrecipProb: Int get() = precipProbs.maxOrNull() ?: 0
    val maxWindGust: Double get() = windGusts.maxOrNull() ?: 0.0

    // True if the workout window (now → now+3h) extends past sunset or starts before sunrise
    val wouldBeDark: Boolean
        get() {
            val now = LocalTime.now()
            val workoutEnd = now.plusHours(3)
            return now.isBefore(sunrise) || workoutEnd.isAfter(sunset)
        }
}
