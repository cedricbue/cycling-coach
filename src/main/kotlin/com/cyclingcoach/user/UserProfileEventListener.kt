package com.cyclingcoach.user

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.garmin.connect.weight.GarminWeightStoredEvent
import com.cyclingcoach.ride.RideCalculatedEvent
import com.cyclingcoach.ride.RideService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class UserProfileEventListener(
    private val userProfileService: UserProfileService,
    private val rideService: RideService,
) {
    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onRideCalculated(event: RideCalculatedEvent) {
        val maxHr = rideService.findMaxHrByRideId(event.rideId) ?: return
        userProfileService.updateMaxHrIfHigher(maxHr)
    }

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onGarminWeightStored(event: GarminWeightStoredEvent) {
        userProfileService.storeWeightMeasurements(event)
    }
}
