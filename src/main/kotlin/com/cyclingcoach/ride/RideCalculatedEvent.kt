package com.cyclingcoach.ride

import java.time.LocalDate

data class RideCalculatedEvent(
    val rideId: Long,
    val activityId: Long,
    val date: LocalDate,
    val tss: Double,
)
