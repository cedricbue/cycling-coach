package com.cyclingcoach.garmin.activity

data class GarminActivityInput(
    val externalId: String,
    val rawTcx: String,
    val rawJson: String? = null,
)
