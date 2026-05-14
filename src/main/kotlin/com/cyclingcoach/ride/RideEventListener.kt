package com.cyclingcoach.ride

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.ftp.FtpBackfillCompleteEvent
import com.cyclingcoach.ftp.FtpTestDetectedEvent
import com.cyclingcoach.ftp.FtpService
import com.cyclingcoach.garmin.activity.GarminActivityStoredEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
internal class RideEventListener(
    private val rideService: RideService,
    private val ftpService: FtpService,
    private val eventPublisher: ApplicationEventPublisher,
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
        rideService.reconcileRidesWithNullTss()
    }

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onFtpTestDetected(event: FtpTestDetectedEvent) {
        val fromDate = ftpService.findLatestTestDateBefore(event.date) ?: event.date

        log.info(
            "FTP {}W detected on {} — recomputing rides in [{}, {}]",
            event.ftpValue.toInt(),
            event.date,
            fromDate,
            event.date,
        )

        rideService.recomputeRidesFrom(fromDate)
        eventPublisher.publishEvent(FtpBackfillCompleteEvent(fromDate))
    }
}
