package com.cyclingcoach.garmin.sync

import com.cyclingcoach.garmin.GarminSyncJob
import com.cyclingcoach.garmin.GarminSyncService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GarminSyncJobTest {
    private val garminSyncService: GarminSyncService = mockk()
    private val job = GarminSyncJob(garminSyncService)

    @Test
    fun `syncAll catches exception and does not propagate`() {
        every { garminSyncService.syncAll() } throws RuntimeException("connection timeout")

        job.syncAll()
    }
}
