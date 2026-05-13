package com.cyclingcoach.garmin.connect.weight

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class GarminWeightService(
    private val garminWeightRepository: GarminWeightRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun storeAll(inputs: List<GarminWeightInput>) {
        if (inputs.isEmpty()) return
        garminWeightRepository.batchUpsert(inputs.map { it.externalId to it.rawJson })
        log.debug("Stored {} garmin weight measurement(s)", inputs.size)
        eventPublisher.publishEvent(
            GarminWeightStoredEvent(
                inputs.map {
                    GarminWeightStoredEvent.Entry(
                        it.date,
                        it.rawJson,
                    )
                },
            ),
        )
    }
}
