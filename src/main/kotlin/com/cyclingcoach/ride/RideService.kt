package com.cyclingcoach.ride

import com.cyclingcoach.activity.ActivityStoredEvent
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Service

@Service
class RideService {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Order(1)
    fun onActivityStored(event: ActivityStoredEvent) {
        log.debug("Activity {} stored on {} — ride calculation not yet implemented", event.activityId, event.date)
        // TODO: parse TCX, compute NP/IF/TSS/best-powers, write Ride row, auto-detect FTP
    }

    @EventListener(ApplicationReadyEvent::class)
    @Order(1)
    fun reconcileOrphanedActivities() {
        // TODO: SELECT activity.id LEFT JOIN ride WHERE ride.id IS NULL
        //       → computeFromActivity(id) for each orphan
    }
}
