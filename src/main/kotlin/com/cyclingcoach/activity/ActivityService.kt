package com.cyclingcoach.activity

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ActivityService(
    private val activityRepository: ActivityRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun storeAll(activities: List<ActivityInput>) {
        for (activity in activities) {
            val id = activityRepository.saveIfNew(activity) ?: continue
            log.debug("Stored new activity {} (id={})", activity.externalId, id)
            eventPublisher.publishEvent(ActivityStoredEvent(id, parseDate(activity.startTimeGmt)))
        }
    }

    fun existsByExternalId(externalId: String): Boolean = activityRepository.existsByExternalId(externalId)

    fun findExistingExternalIds(externalIds: Collection<String>): Set<String> = activityRepository.findExistingExternalIds(externalIds)

    fun findLatestStartTime(): LocalDate? = activityRepository.findLatestStartTime()?.let { parseDate(it) }

    private fun parseDate(startTimeGmt: String): LocalDate {
        val formatters =
            listOf(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            )
        for (fmt in formatters) {
            try {
                return LocalDateTime.parse(startTimeGmt, fmt).toLocalDate()
            } catch (_: Exception) {
            }
        }
        return LocalDate.parse(startTimeGmt.take(10))
    }
}
