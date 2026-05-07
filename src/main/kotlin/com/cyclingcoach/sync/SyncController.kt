package com.cyclingcoach.sync

import com.cyclingcoach.generated.api.SyncApi
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SyncController(
    private val asyncGarminSyncService: AsyncGarminSyncService,
) : SyncApi {
    override fun triggerSync(): ResponseEntity<Unit> {
        asyncGarminSyncService.syncActivities()
        return ResponseEntity.accepted().build()
    }
}
