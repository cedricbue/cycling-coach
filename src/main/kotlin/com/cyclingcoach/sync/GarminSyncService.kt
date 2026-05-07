package com.cyclingcoach.sync

import com.cyclingcoach.activity.ActivityInput
import com.cyclingcoach.activity.ActivityService
import com.cyclingcoach.client.garmin.GarminClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GarminSyncService(
    private val garminClient: GarminClient,
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
            log.debug("No valid Garmin session — attempting re-authentication before sync")
            garminClient.login(garminProperties.email, garminProperties.password)
        }
        val since =
            activityService.findLatestStartTime() ?: java.time.LocalDate
                .now()
                .minusDays(garminProperties.sync.initialFetchDays.toLong())

        log.info("Starting Garmin activity sync since {}", since)

        val pageSize = garminProperties.sync.pageSize
        var offset = 0
        var newCount = 0

        val concurrentDownloadLimit = Semaphore(garminProperties.sync.maxConcurrentDownloads)

        while (true) {
            val activityPage = garminClient.getActivities(since = since, limit = pageSize, start = offset)
            log.debug("Fetched {} activities from Garmin Connect (start={})", activityPage.size, offset)

            val alreadySynced = activityService.findExistingExternalIds(activityPage.map { it.activityId.toString() })
            val newActivities = activityPage.filter { it.activityId.toString() !in alreadySynced }

            val activitiesWithTcx =
                runBlocking {
                    newActivities
                        .map { activity ->
                            async {
                                concurrentDownloadLimit.withPermit {
                                    activity to garminClient.downloadTcx(activity.activityId)
                                }
                            }
                        }.awaitAll()
                }

            activityService.storeAll(
                activitiesWithTcx.map { (activity, tcx) ->
                    ActivityInput(
                        externalId = activity.activityId.toString(),
                        name = activity.activityName ?: "Untitled",
                        startTimeGmt = activity.startTimeGmt ?: "",
                        rawTcx = tcx,
                    )
                },
            )
            newCount += activitiesWithTcx.size

            if (activityPage.size < pageSize) break
            offset += pageSize
        }
        log.info("Sync complete — {} new activities stored", newCount)
    }
}
