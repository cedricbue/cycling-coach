package com.cyclingcoach.garmin

import com.cyclingcoach.generated.api.GarminApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class GarminController(
    private val garminSyncService: GarminSyncService,
) : GarminApi {
    override fun triggerGarminSync(): ResponseEntity<Unit> {
        garminSyncService.syncAll()
        return ResponseEntity.accepted().build()
    }
}
