package com.cyclingcoach.ride

import com.cyclingcoach.activity.ActivityRepository
import com.cyclingcoach.activity.ActivityStoredEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class RideService(
    private val activityRepository: ActivityRepository,
    private val rideRepository: RideRepository,
    private val userProfileRepository: UserProfileRepository,
    private val parser: ActivityFileParser,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Order(1)
    fun onActivityStored(event: ActivityStoredEvent) {
        computeForActivity(event.activityId, event.date)
    }

    @EventListener(ApplicationReadyEvent::class)
    @Order(1)
    fun reconcileOrphanedActivities() {
        val orphans = rideRepository.findActivityIdsWithoutRide()
        if (orphans.isNotEmpty()) {
            log.info("Reconciling {} activities without ride rows", orphans.size)
            orphans.forEach { computeForActivity(it, null) }
        }
    }

    private fun computeForActivity(activityId: Long, eventDate: LocalDate?) {
        if (rideRepository.existsByActivityId(activityId)) return

        val rawTcx = activityRepository.findRawTcxById(activityId)
        if (rawTcx == null) {
            log.warn("Activity {} has no raw TCX — skipping ride calculation", activityId)
            return
        }

        if (!parser.supports(rawTcx)) {
            log.warn("No parser supports activity {} format — skipping", activityId)
            return
        }

        val rideData = parser.parse(rawTcx)
        val densePower = RideCalculator.expandToDenseStream(rideData.powerWatts, rideData.timestamps)

        val np = RideCalculator.calculateNormalizedPower(densePower)
        val ftp = userProfileRepository.findCurrentFtp()
        val weightKg = userProfileRepository.findCurrentWeightKg()

        val avgPower = if (densePower.isNotEmpty()) densePower.average() else null
        val maxPower = densePower.maxOrNull()?.toDouble()

        val hrValues = rideData.heartRateBpm.filterNotNull()
        val avgHr = if (hrValues.isNotEmpty()) hrValues.average() else null
        val maxHr = hrValues.maxOrNull()?.toDouble()

        val cadValues = rideData.cadenceRpm.filterNotNull()
        val avgCadence = if (cadValues.isNotEmpty()) cadValues.average() else null
        val maxCadence = cadValues.maxOrNull()?.toDouble()

        val speedValues = rideData.speedMps.filterNotNull()
        val avgSpeed = if (speedValues.isNotEmpty()) speedValues.average() else null

        val altitudes = rideData.altitudeMeters.filterNotNull()
        val distances = rideData.distanceMeters.filterNotNull()

        val intensityFactor = if (np != null && ftp != null) RideCalculator.calculateIntensityFactor(np, ftp) else null
        val tss = if (np != null && ftp != null) RideCalculator.calculateTss(rideData.durationSeconds, np, ftp) else null
        val vi = if (np != null && avgPower != null) RideCalculator.calculateVariabilityIndex(np, avgPower) else null
        val ef = if (np != null) RideCalculator.calculateEfficiencyFactor(np, avgHr) else null
        val wattsPerKg = if (np != null && weightKg != null) np / weightKg else null

        val date = eventDate ?: deriveDate(rideData.timestamps)

        val input = RideInput(
            activityId = activityId,
            date = date,
            distanceMeters = rideData.totalDistanceMeters,
            elevationGain = if (altitudes.isNotEmpty()) RideCalculator.calculateElevationGain(altitudes) else null,
            elevationDescent = if (altitudes.isNotEmpty()) RideCalculator.calculateElevationDescent(altitudes) else null,
            durationSeconds = rideData.durationSeconds,
            avgPower = avgPower,
            maxPower = maxPower,
            avgHr = avgHr,
            maxHr = maxHr,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgGrade = if (altitudes.size >= 2 && distances.size >= 2) RideCalculator.calculateAvgGrade(altitudes, distances) else null,
            maxGrade = if (altitudes.size >= 2 && distances.size >= 2) RideCalculator.calculateMaxGrade(altitudes, distances) else null,
            normalizedPower = np,
            intensityFactor = intensityFactor,
            tss = tss,
            bestPower5s = RideCalculator.calculateBestPower(densePower, 5),
            bestPower30s = RideCalculator.calculateBestPower(densePower, 30),
            bestPower1min = RideCalculator.calculateBestPower(densePower, 60),
            bestPower5min = RideCalculator.calculateBestPower(densePower, 300),
            bestPower10min = RideCalculator.calculateBestPower(densePower, 600),
            bestPower20min = RideCalculator.calculateBestPower(densePower, 1200),
            bestPower60min = RideCalculator.calculateBestPower(densePower, 3600),
            wattsPerKg = wattsPerKg,
            ftp = ftp,
            avgSpeedMps = avgSpeed,
            maxSpeedMps = rideData.maxSpeedMps,
            variabilityIndex = vi,
            efficiencyFactor = ef,
        )

        val rideId = rideRepository.save(input)
        log.info(
            "Ride {} calculated for activity {} on {}: NP={}W TSS={}",
            rideId, activityId, date,
            np?.let { "%.1f".format(it) },
            tss?.let { "%.1f".format(it) },
        )
        eventPublisher.publishEvent(RideCalculatedEvent(rideId, activityId, date, tss ?: 0.0))
    }

    private fun deriveDate(timestamps: List<Instant>): LocalDate =
        timestamps.firstOrNull()?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: LocalDate.now()
}
