package com.cyclingcoach.sync

import com.cyclingcoach.generated.api.SyncApi
import com.cyclingcoach.generated.model.GarminAuthRequest
import com.cyclingcoach.generated.model.SyncStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SyncController(
    private val garminSyncService: GarminSyncService,
    private val garminSessionRepository: GarminSessionRepository,
) : SyncApi {
    override fun authenticate(garminAuthRequest: GarminAuthRequest): ResponseEntity<SyncStatus> {
        garminSyncService.authenticate(garminAuthRequest.username, garminAuthRequest.password)
        return ResponseEntity.ok(buildStatus())
    }

    override fun triggerSync(): ResponseEntity<SyncStatus> {
        // TODO rethink this design choide
        Thread.ofVirtual().start { garminSyncService.syncActivities() }
        return ResponseEntity.accepted().body(buildStatus().copy(message = "Sync started"))
    }

    override fun getSyncStatus(): ResponseEntity<SyncStatus> = ResponseEntity.ok(buildStatus())

    private fun buildStatus(): SyncStatus {
        val tokens = garminSessionRepository.loadLatest()
        return SyncStatus(
            sessionValid = tokens?.isExpired()?.not() ?: false,
            sessionExpiresAt =
                tokens?.accessTokenExpiresAt?.toString()?.let {
                    java.time.OffsetDateTime.parse(it.replace("Z", "+00:00"))
                },
            message =
                if (tokens == null) {
                    "No session — call POST /api/sync/authenticate"
                } else if (tokens.isExpired()) {
                    "Session expired — call POST /api/sync/authenticate"
                } else {
                    "Session valid"
                },
        )
    }
}
