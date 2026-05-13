package com.cyclingcoach.garmin.connect.weight

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.garmin.GarminProperties
import com.cyclingcoach.garmin.GarminSyncable
import com.cyclingcoach.garmin.connect.client.GarminConnect
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Service
class GarminWeightSyncService(
    private val garminClient: GarminConnect,
    private val garminWeightService: GarminWeightService,
    private val garminProperties: GarminProperties,
    private val syncCursorRepository: GarminWeightSyncCursorRepository,
) : GarminSyncable {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override val name: String = "weight"

    @Async(VIRTUAL_THREAD_EXECUTOR)
    override fun sync(): CompletableFuture<Void> {
        val since =
            syncCursorRepository.findSince()
                ?: LocalDate.now().minusDays(
                    garminProperties.connect.weight.initialFetchDays
                        .toLong(),
                )
        val until = LocalDate.now()

        log.info("Syncing weight data from Garmin Connect since {}", since)

        val weights = garminClient.getWeights(startDate = since, endDate = until)
        log.debug("Garmin returned {} weight entries", weights.size)

        val inputs =
            weights.mapNotNull { entry ->
                val samplePk = entry.samplePk
                if (samplePk == null) {
                    log.debug("Skipping weight entry with no samplePk on {}", entry.calendarDate)
                    return@mapNotNull null
                }
                val date =
                    entry.calendarDate
                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (date == null) {
                    log.debug("Skipping weight entry with unparseable date: {}", entry.calendarDate)
                    return@mapNotNull null
                }
                GarminWeightInput(
                    externalId = samplePk.toString(),
                    date = date,
                    rawJson = mapper.writeValueAsString(entry),
                )
            }

        garminWeightService.storeAll(inputs)
        syncCursorRepository.updateSince(until)

        log.info("Weight sync complete — {} measurement(s) stored/updated", inputs.size)
        return CompletableFuture.completedFuture(null)
    }
}
