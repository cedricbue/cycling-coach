package com.cyclingcoach.user

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.ftp.FtpTestDetectedEvent
import com.cyclingcoach.garmin.connect.weight.GarminWeightStoredEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class UserProfileEventListener(
    private val userProfileService: UserProfileService,
) {
    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onFtpTestDetected(event: FtpTestDetectedEvent) {
        userProfileService.updateFtp(event)
    }

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onGarminWeightStored(event: GarminWeightStoredEvent) {
        userProfileService.storeWeightMeasurements(event)
    }
}
