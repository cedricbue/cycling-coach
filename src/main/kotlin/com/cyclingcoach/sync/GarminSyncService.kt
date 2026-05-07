package com.cyclingcoach.sync

import com.cyclingcoach.activity.ActivityService
import com.cyclingcoach.client.garmin.GarminClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GarminSyncService(
    private val garminClient: GarminClient,
    private val garminTokenStore: GarminTokenStore,
    private val activityService: ActivityService,
    private val garminProperties: GarminProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun authenticate(
        username: String,
        password: String,
    ) {
        garminClient.login(username, password)
        log.info("Garmin authentication successful — DI tokens saved")
    }

    fun hasValidSession(): Boolean = garminClient.hasValidSession()

    fun syncActivities() {
        if (!garminClient.hasValidSession()) {
            log.warn("No valid Garmin session available — sync aborted")
            return
        }
        log.info("Starting Garmin activity sync")

        val since = activityService.findLastSyncTime()?.toLocalDate()

        val activities = garminClient.getActivities(
            since = since ?: java.time.LocalDate.now()
                .minusDays(garminProperties.sync.initialFetchDays.toLong()),
        )
        log.info("Fetched {} activities from Garmin Connect", activities.size)

        var newCount = 0
        for (activity in activities) {
            val externalId = activity.activityId.toString()
            if (activityService.existsByExternalId(externalId)) continue

            val tcx = garminClient.downloadTcx(activity.activityId)

            activityService.storeIfNew(
                externalId = externalId,
                name = activity.activityName ?: "Untitled",
                startTimeGmt = activity.startTimeGmt ?: "",
                rawTcx = tcx,
            )
            newCount++
        }
        log.info("Sync complete — {} new activities stored", newCount)
    }
}
