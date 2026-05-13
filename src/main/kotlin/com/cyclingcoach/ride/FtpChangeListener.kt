package com.cyclingcoach.ride

import com.cyclingcoach.ftp.FtpBackfillCompleteEvent
import com.cyclingcoach.ftp.FtpTestDetectedEvent
import com.cyclingcoach.ftp.FtpTestRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FtpChangeListener(
    private val rideService: RideService,
    private val ftpTestRepository: FtpTestRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onFtpTestDetected(event: FtpTestDetectedEvent) {
        val prevTest = ftpTestRepository.findLatestBefore(event.date)
        val fromDate = prevTest?.date ?: event.date

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
