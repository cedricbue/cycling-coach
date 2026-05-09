package com.cyclingcoach.garmin.connect.weight

import java.time.LocalDate

data class GarminWeightInput(
    val externalId: String,
    val date: LocalDate,
    val rawJson: String,
)
