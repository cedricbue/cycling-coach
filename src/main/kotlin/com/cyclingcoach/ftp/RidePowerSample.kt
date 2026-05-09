package com.cyclingcoach.ftp

/**
 * Best-power readings projected from a historical ride — input to FTP estimation.
 * Fields are null when the ride was too short to produce a valid value for that duration.
 */
data class RidePowerSample(
    val durationSeconds: Double,
    val bestPower5min: Double?,
    val bestPower10min: Double?,
    val bestPower20min: Double?,
    val bestPower60min: Double?,
)
