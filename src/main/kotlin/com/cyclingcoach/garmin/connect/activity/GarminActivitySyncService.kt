package com.cyclingcoach.garmin.connect.activity

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.garmin.GarminProperties
import com.cyclingcoach.garmin.GarminSyncable
import com.cyclingcoach.garmin.activity.GarminActivityInput
import com.cyclingcoach.garmin.activity.GarminActivityService
import com.cyclingcoach.garmin.activity.GarminActivitySyncCursorRepository
import com.cyclingcoach.garmin.connect.client.GarminConnect
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

@Service
class GarminActivitySyncService(
    private val garminClient: GarminConnect,
    private val garminActivityService: GarminActivityService,
    private val garminProperties: GarminProperties,
    private val syncCursorRepository: GarminActivitySyncCursorRepository,
) : GarminSyncable {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name: String = "activities"

    @Async(VIRTUAL_THREAD_EXECUTOR)
    override fun sync(): CompletableFuture<Void> {
        val since =
            syncCursorRepository.findSince()
                ?: LocalDate
                    .now()
                    .minusDays(
                        garminProperties.connect.activity.initialFetchDays
                            .toLong(),
                    )

        log.info("Starting Garmin activity sync since {}", since)

        val pageSize = garminProperties.connect.activity.pageSize
        var offset = 0
        var newCount = 0
        val concurrentDownloadLimit = Semaphore(garminProperties.connect.activity.maxConcurrentDownloads)

        while (true) {
            val activityPage = garminClient.getActivities(since = since, limit = pageSize, start = offset)
            log.debug("Fetched {} activities from Garmin Connect (start={})", activityPage.size, offset)

            val alreadySynced =
                garminActivityService.findExistingExternalIds(activityPage.map { it.activity.activityId.toString() })
            val newActivities = activityPage.filter { it.activity.activityId.toString() !in alreadySynced }

            log.debug("{} new activities to download (page start={})", newActivities.size, offset)

            val activitiesWithTcx =
                runBlocking {
                    newActivities
                        .map { activityWithRaw ->
                            async {
                                concurrentDownloadLimit.withPermit {
                                    activityWithRaw to garminClient.downloadTcx(activityWithRaw.activity.activityId)
                                }
                            }
                        }.awaitAll()
                }

            garminActivityService.storeAll(
                activitiesWithTcx.map { (activityWithRaw, tcx) ->
                    GarminActivityInput(
                        externalId = activityWithRaw.activity.activityId.toString(),
                        rawTcx = tcx,
                        rawJson = activityWithRaw.rawJson,
                    )
                },
            )
            newCount += activitiesWithTcx.size

            if (activityPage.size < pageSize) break
            offset += pageSize
        }
        garminActivityService.findLatestStartTime()?.let { syncCursorRepository.updateSince(it) }
        log.info("Activity sync complete — {} new activities stored", newCount)
        return CompletableFuture.completedFuture(null)
    }
}
