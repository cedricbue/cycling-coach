package com.cyclingcoach.ride

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RideService(
    private val rideRepository: RideRepository,
    private val rideComputeService: RideComputeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun findNameByRideId(rideId: Long): String? = rideRepository.findNameByRideId(rideId)

    fun findMetricsById(rideId: Long): RideMetrics? = rideRepository.findMetricsById(rideId)

    fun computeForActivity(activityId: Long) = rideComputeService.compute(activityId, null)

    fun reconcileOrphanedActivities() {
        val orphans = rideRepository.findActivityIdsWithoutRide()
        if (orphans.isNotEmpty()) {
            log.info("Reconciling {} activities without ride rows", orphans.size)
            orphans.forEach { rideComputeService.computeAsync(it, null) }
        }
    }
}
