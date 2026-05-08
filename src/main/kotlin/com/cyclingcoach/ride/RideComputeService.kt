package com.cyclingcoach.ride

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.ftp.FtpEstimationService
import com.cyclingcoach.garmin.activity.GarminActivityService
import com.cyclingcoach.garmin.connect.client.GarminActivity
import com.cyclingcoach.user.UserProfileService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@Component
class RideComputeService(
    private val garminActivityService: GarminActivityService,
    private val rideRepository: RideRepository,
    private val userProfileService: UserProfileService,
    private val ftpEstimationService: FtpEstimationService,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(VIRTUAL_THREAD_EXECUTOR)
    fun computeAsync(
        activityId: Long,
        eventDate: LocalDate?,
    ) = compute(activityId, eventDate)

    fun compute(
        activityId: Long,
        eventDate: LocalDate?,
    ) {
        val garminJson =
            garminActivityService.findRawJsonById(activityId)?.let { json ->
                runCatching { objectMapper.readValue(json, GarminActivity::class.java) }
                    .onFailure { log.warn("Activity {} — failed to parse raw_json: {}", activityId, it.message) }
                    .getOrNull()
            }

        if (garminJson == null) {
            log.warn("Activity {} has no parseable JSON — skipping ride calculation", activityId)
            return
        }

        if (!garminJson.isRide()) {
            log.debug("Activity {} is not a cycling activity (type={}) — skipping", activityId, garminJson.activityType?.typeKey)
            return
        }

        val externalId = garminActivityService.findExternalIdById(activityId)
        if (externalId == null) {
            log.warn("Activity {} has no external_id — skipping", activityId)
            return
        }

        val date = eventDate ?: garminJson.startTimeGmt?.let { parseGmtDate(it) } ?: LocalDate.now()
        val ftp = resolveRideFtp(date)
        val weightKg = userProfileService.findLatestWeightKg()

        val np = garminJson.normPower
        val intensityFactor = if (np != null && ftp != null) RideCalculator.calculateIntensityFactor(np, ftp) else null
        val tss = if (np != null && ftp != null) RideCalculator.calculateTss(garminJson.duration ?: 0.0, np, ftp) else null
        val vi = if (np != null && garminJson.avgPower != null) RideCalculator.calculateVariabilityIndex(np, garminJson.avgPower) else null
        val ef = if (np != null) RideCalculator.calculateEfficiencyFactor(np, garminJson.averageHR) else null
        val wattsPerKg = if (np != null && weightKg != null) np / weightKg else null

        val input =
            RideInput(
                activityId = activityId,
                externalId = externalId,
                date = date,
                name = garminJson.activityName,
                startTime = garminJson.startTimeGmt,
                manufacturer = garminJson.manufacturer,
                distanceMeters = garminJson.distance,
                elevationGain = garminJson.elevationGain,
                elevationDescent = garminJson.elevationLoss,
                durationSeconds = garminJson.duration,
                avgPower = garminJson.avgPower,
                maxPower = garminJson.maxPower,
                avgHr = garminJson.averageHR,
                maxHr = garminJson.maxHR,
                avgCadence = garminJson.averageCadence,
                maxCadence = garminJson.maxCadence,
                avgGrade = null,
                maxGrade = null,
                normalizedPower = np,
                intensityFactor = intensityFactor,
                tss = tss,
                bestPower5s = garminJson.maxAvgPower5s,
                bestPower30s = garminJson.maxAvgPower30s,
                bestPower1min = garminJson.maxAvgPower1min,
                bestPower5min = garminJson.maxAvgPower5min,
                bestPower10min = garminJson.maxAvgPower10min,
                bestPower20min = garminJson.maxAvgPower20min,
                bestPower60min = garminJson.maxAvgPower60min,
                wattsPerKg = wattsPerKg,
                ftp = ftp,
                avgSpeedMps = garminJson.averageSpeed,
                maxSpeedMps = garminJson.maxSpeed,
                variabilityIndex = vi,
                efficiencyFactor = ef,
            )

        val rideId = rideRepository.save(input)
        log.info(
            "Ride {} saved for activity {} on {}: NP={}W TSS={}",
            rideId,
            activityId,
            date,
            np?.let { "%.1f".format(it) },
            tss?.let { "%.1f".format(it) },
        )
        eventPublisher.publishEvent(RideCalculatedEvent(rideId, activityId, date, tss ?: 0.0))
    }

    private fun resolveRideFtp(rideDate: LocalDate): Double? {
        val profileFtp = userProfileService.findCurrentFtp()
        if (profileFtp != null) return profileFtp
        val samples = rideRepository.findPowerSamplesBefore(rideDate)
        return ftpEstimationService.estimate(samples)
    }

    private fun parseGmtDate(startTimeGmt: String): LocalDate? = runCatching { LocalDate.parse(startTimeGmt.take(10)) }.getOrNull()
}
