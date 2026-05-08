package com.cyclingcoach.garmin

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class GarminSyncJob(
    private val garminSyncService: GarminSyncService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Runs every 6 hours (default) **/
    @Scheduled(fixedRateString = "\${garmin.interval-ms:21600000}")
    fun syncAll() {
        try {
            garminSyncService.syncAll()
        } catch (e: Exception) {
            log.error("Garmin sync failed — will retry on next scheduled run: {}", e.message)
        }
    }
}
