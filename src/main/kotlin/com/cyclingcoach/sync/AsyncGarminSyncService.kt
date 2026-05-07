package com.cyclingcoach.sync

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AsyncGarminSyncService(
    private val garminSyncService: GarminSyncService,
) {
    @Async(VIRTUAL_THREAD_EXECUTOR)
    fun syncActivities() {
        garminSyncService.syncActivities()
    }
}
