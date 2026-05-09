package com.cyclingcoach.tcx

import java.time.Instant

data class TcxData(
    val timestamps: List<Instant>,
    val powerWatts: List<Int?>,
    val heartRateBpm: List<Int?>,
    val cadenceRpm: List<Int?>,
    val speedMps: List<Double?>,
    val altitudeMeters: List<Double?>,
    val distanceMeters: List<Double?>,
    val durationSeconds: Double,
    val totalDistanceMeters: Double,
    val maxSpeedMps: Double?,
)
