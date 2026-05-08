package com.cyclingcoach.ride

import com.cyclingcoach.activity.ActivityStoredEvent
import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class RideService(
    private val rideRepository: RideRepository,
    private val rideComputeTask: RideComputeService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onActivityStored(event: ActivityStoredEvent) {
        rideComputeTask.compute(event.activityId, event.date)
    }

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileOrphanedActivities() {
        val orphans = rideRepository.findActivityIdsWithoutRide()
        if (orphans.isNotEmpty()) {
            log.info("Reconciling {} activities without ride rows", orphans.size)
            orphans.forEach { rideComputeTask.computeAsync(it, null) }
        }
    }
}
