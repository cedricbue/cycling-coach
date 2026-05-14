package com.cyclingcoach.ride

import com.cyclingcoach.generated.model.RideDetail
import com.cyclingcoach.generated.model.RideMetrics as ApiRideMetrics
import com.cyclingcoach.generated.model.RidePage
import com.cyclingcoach.generated.model.RideSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RideService(
    private val rideRepository: RideRepository,
    private val rideComputeService: RideComputeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listRides(
        page: Int,
        size: Int,
    ): RidePage {
        val total = rideRepository.countRides()
        val rows = rideRepository.findRidePage(page, size)
        return RidePage(
            content = rows.map { r ->
                RideSummary(
                    id = r.id,
                    name = r.name,
                    startTime = r.startTime,
                    manufacturer = r.manufacturer,
                    date = r.date,
                    distanceKm = r.distanceKm,
                    durationSeconds = r.durationSeconds,
                    elevationGainM = r.elevationGainM,
                    avgSpeedKmh = r.avgSpeedKmh,
                    avgPowerW = r.avgPowerW,
                    normalizedPowerW = r.normalizedPowerW,
                    tss = r.tss,
                    intensityFactor = r.intensityFactor,
                )
            },
            totalElements = total,
            page = page,
            propertySize = size,
        )
    }

    fun getRide(rideId: Long): RideDetail? =
        rideRepository.findRideDetail(rideId)?.let { r ->
            RideDetail(
                id = r.id,
                externalId = r.externalId,
                name = r.name,
                startTime = r.startTime,
                manufacturer = r.manufacturer,
                metrics = ApiRideMetrics(
                    distanceKm = r.distanceKm,
                    elevationGainM = r.elevationGainM,
                    elevationDescentM = r.elevationDescentM,
                    durationSeconds = r.durationSeconds,
                    avgPowerW = r.avgPowerW,
                    maxPowerW = r.maxPowerW,
                    avgHrBpm = r.avgHrBpm,
                    maxHrBpm = r.maxHrBpm,
                    avgCadenceRpm = r.avgCadenceRpm,
                    maxCadenceRpm = r.maxCadenceRpm,
                    normalizedPowerW = r.normalizedPowerW,
                    intensityFactor = r.intensityFactor,
                    tss = r.tss,
                    ftpAtRide = r.ftpAtRide,
                    wattsPerKg = r.wattsPerKg,
                    bestPower5sW = r.bestPower5sW,
                    bestPower30sW = r.bestPower30sW,
                    bestPower1minW = r.bestPower1minW,
                    bestPower5minW = r.bestPower5minW,
                    bestPower10minW = r.bestPower10minW,
                    bestPower20minW = r.bestPower20minW,
                    bestPower60minW = r.bestPower60minW,
                    rpe = r.rpe,
                    coachSummary = r.coachSummary,
                    notes = r.notes,
                ),
            )
        }

    fun findNameByRideId(rideId: Long): String? = rideRepository.findNameByRideId(rideId)

    fun findMaxHrByRideId(rideId: Long): Int? = rideRepository.findMaxHrByRideId(rideId)

    fun findMetricsById(rideId: Long): RideMetrics? = rideRepository.findMetricsById(rideId)

    fun computeForActivity(activityId: Long) = rideComputeService.compute(activityId, null)

    fun recomputeRidesFrom(fromDate: LocalDate) {
        val activityIds = rideRepository.findActivityIdsSince(fromDate)
        if (activityIds.isNotEmpty()) {
            log.info("Recomputing {} rides from {} onwards after FTP change", activityIds.size, fromDate)
            activityIds.forEach { rideComputeService.compute(it, null, publishEvent = false) }
        }
    }

    fun reconcileOrphanedActivities() {
        val orphans = rideRepository.findActivityIdsWithoutRide()
        if (orphans.isNotEmpty()) {
            log.info("Reconciling {} activities without ride rows", orphans.size)
            orphans.forEach { rideComputeService.computeAsync(it, null) }
        }
    }

    fun reconcileRidesWithNullTss() {
        val activityIds = rideRepository.findActivityIdsWithNullTss()
        if (activityIds.isNotEmpty()) {
            log.info("Reconciling {} rides with null TSS — applying point-in-time FTP", activityIds.size)
            activityIds.forEach { rideComputeService.computeAsync(it, null) }
        }
    }
}
