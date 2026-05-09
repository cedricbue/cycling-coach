package com.cyclingcoach.ride

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.garmin.activity.GarminActivityStoredEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class RideEventListener(
    private val rideService: RideService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onGarminActivityStored(event: GarminActivityStoredEvent) {
        rideService.computeForActivity(event.garminActivityId)
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        rideService.reconcileOrphanedActivities()
    }
}
