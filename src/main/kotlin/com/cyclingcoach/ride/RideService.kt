package com.cyclingcoach.ride

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RideService(
    private val rideRepository: RideRepository,
    private val rideComputeService: RideComputeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
