package com.cyclingcoach.user

import com.cyclingcoach.garmin.connect.client.GarminWeightEntry
import com.cyclingcoach.garmin.connect.weight.GarminWeightStoredEvent
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class UserProfileService(
    private val weightRepository: WeightRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun findLatestWeightKg(): Double? = weightRepository.findLatestWeight()

    fun findWeightKgAt(date: LocalDate): Double? = weightRepository.findWeightAtOrBefore(date)

    fun findMaxHr(): Int? = userProfileRepository.findMaxHr()

    fun updateMaxHrIfHigher(maxHr: Int) {
        if (maxHr <= 0) return
        userProfileRepository.updateMaxHrIfHigher(maxHr)
    }

    fun storeWeightMeasurements(event: GarminWeightStoredEvent) {
        for (entry in event.entries) {
            val weightEntry =
                runCatching { mapper.readValue(entry.rawJson, GarminWeightEntry::class.java) }
                    .onFailure { log.warn("Failed to parse weight JSON for {}: {}", entry.date, it.message) }
                    .getOrNull() ?: continue

            val weightKg = weightEntry.weightKg() ?: continue
            weightRepository.upsert(entry.date, weightKg)
        }
        log.debug("Stored {} weight measurement(s)", event.entries.size)
    }
}
