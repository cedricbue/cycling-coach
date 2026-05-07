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

    /**
     * Persists the activity if it hasn't been seen before (dedup on externalId).
     * Fires ActivityStoredEvent for new activities so downstream processing is triggered.
     * Returns true if the activity was new and stored.
     */
    fun storeIfNew(
        externalId: String,
        name: String?,
        startTimeGmt: String,
        rawTcx: String,
    ): Boolean {
        if (activityRepository.existsByExternalId(externalId)) {
            log.debug("Activity {} already exists — skipping", externalId)
            return false
        }
        val activityId = activityRepository.save(externalId, name, startTimeGmt, rawTcx)
        log.info("Stored new activity {} (id={})", externalId, activityId)

        val date = parseDate(startTimeGmt)
        eventPublisher.publishEvent(ActivityStoredEvent(activityId, date))
        return true
    }

    fun existsByExternalId(externalId: String): Boolean = activityRepository.existsByExternalId(externalId)

    fun findLastSyncTime(): LocalDateTime? = activityRepository.findLastSyncTime()

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
