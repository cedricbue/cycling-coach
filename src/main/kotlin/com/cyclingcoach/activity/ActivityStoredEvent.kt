package com.cyclingcoach.activity

import java.time.LocalDate

/**
 * Published by ActivityService whenever a new activity is persisted for the first time.
 * Downstream listeners (RideService, TrainingLoadService) subscribe to trigger their processing.
 */
data class ActivityStoredEvent(
    val activityId: Long,
    val date: LocalDate,
)
