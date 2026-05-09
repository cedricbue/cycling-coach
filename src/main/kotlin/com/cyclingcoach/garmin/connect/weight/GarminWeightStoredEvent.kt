package com.cyclingcoach.garmin.connect.weight

import java.time.LocalDate

data class GarminWeightStoredEvent(
    val entries: List<Entry>,
) {
    data class Entry(
        val date: LocalDate,
        val rawJson: String,
    )
}
