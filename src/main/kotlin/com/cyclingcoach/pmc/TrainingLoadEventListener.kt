package com.cyclingcoach.pmc

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.ftp.FtpBackfillCompleteEvent
import com.cyclingcoach.ride.RideCalculatedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class TrainingLoadEventListener(
    private val trainingLoadService: TrainingLoadService,
) {
    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onRideCalculated(event: RideCalculatedEvent) {
        trainingLoadService.recalculateFrom(event.date)
    }

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onFtpBackfillComplete(event: FtpBackfillCompleteEvent) {
        trainingLoadService.recalculateFrom(event.fromDate)
    }
}
