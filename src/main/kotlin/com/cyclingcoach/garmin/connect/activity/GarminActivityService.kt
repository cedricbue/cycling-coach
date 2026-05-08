package com.cyclingcoach.garmin.activity

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GarminActivityService(
    private val garminActivityRepository: GarminActivityRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun storeAll(activities: List<GarminActivityInput>) {
        for (activity in activities) {
            val isNewActivity = !garminActivityRepository.existsByExternalId(activity.externalId)
            val id = garminActivityRepository.upsert(activity)
            log.debug("Stored garmin activity {} (id={})", activity.externalId, id)

            if (isNewActivity) {
                eventPublisher.publishEvent(
                    GarminActivityStoredEvent(
                        garminActivityId = id,
                        externalId = activity.externalId,
                    ),
                )
            }
        }
    }

    fun findRawJsonById(activityId: Long): String? = garminActivityRepository.findRawJsonById(activityId)

    fun findExternalIdById(activityId: Long): String? = garminActivityRepository.findExternalIdById(activityId)

    fun existsByExternalId(externalId: String): Boolean = garminActivityRepository.existsByExternalId(externalId)

    fun findExistingExternalIds(externalIds: Collection<String>): Set<String> = garminActivityRepository.findExistingExternalIds(externalIds)

    fun findLatestStartTime(): LocalDate? = garminActivityRepository.findLatestStartTime()
}
