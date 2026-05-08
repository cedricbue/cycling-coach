package com.cyclingcoach.ftp

import com.cyclingcoach.ride.RideCalculatedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FtpEventListener(
    private val ftpTestDetectionService: FtpTestDetectionService,
) {
    @EventListener
    fun onRideCalculated(event: RideCalculatedEvent) {
        ftpTestDetectionService.detectFtpTest(event)
    }
}
