package com.cyclingcoach.garmin

import com.cyclingcoach.garmin.connect.client.GarminConnect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

/**
 * Central orchestrator — ensures a valid Garmin session, then delegates
 * to every [GarminSyncable] bean discovered by Spring (activity, weight, …).
 */
@Service
class GarminSyncService(
    private val garminClient: GarminConnect,
    private val garminProperties: GarminProperties,
    private val syncables: List<GarminSyncable>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun authenticate(
        username: String,
        password: String,
    ) {
        garminClient.login(username, password)
        log.info("Garmin authentication successful — DI tokens saved")
    }

    fun syncAll() {
        ensureGarminConnectSession()

        val futures =
            syncables.map { syncable ->
                log.info("Running Garmin {} sync", syncable.name)
                syncable
                    .sync()
                    .exceptionally { ex ->
                        log.warn(
                            "Garmin {} sync failed — will retry on next scheduled run: {}",
                            syncable.name,
                            ex.message,
                        )
                        null
                    }
            }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
    }

    private fun ensureGarminConnectSession() {
        if (!garminClient.hasValidSession()) {
            log.debug("No valid Garmin session — attempting re-authentication before sync")
            garminClient.login(garminProperties.email, garminProperties.password)
        }
    }
}
