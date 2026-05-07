package com.cyclingcoach.sync

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GarminSyncJob(
    private val garminSyncService: GarminSyncService,
    private val garminProperties: GarminProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Authenticate on startup using env-var credentials if no valid session exists yet. */
    @EventListener(ApplicationReadyEvent::class)
    fun authenticateOnStartup() {
        if (garminSyncService.hasValidSession()) {
            log.info("Garmin session still valid — skipping re-authentication")
            return
        }
        log.info("Authenticating with Garmin Connect")
        garminSyncService.authenticate(garminProperties.email, garminProperties.password)
    }

    /** Runs every 6 hours (default); override via sync.interval-ms property. */
    @Scheduled(fixedRateString = "\${sync.interval-ms:21600000}", initialDelayString = "5000")
    fun syncActivities() {
        if (!garminSyncService.hasValidSession()) {
            log.warn("No valid Garmin session — skipping scheduled sync")
            return
        }
        try {
            garminSyncService.syncActivities()
        } catch (e: Exception) {
            log.error("Garmin sync failed — will retry on next scheduled run: {}", e.message)
        }
    }
}
