package com.cyclingcoach.garmin.connect.client

data class GarminActivityWithRaw(
    val activity: GarminActivity,
    val rawJson: String,
)
