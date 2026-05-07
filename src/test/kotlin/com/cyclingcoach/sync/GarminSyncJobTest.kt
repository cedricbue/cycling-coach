package com.cyclingcoach.sync

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GarminSyncJobTest {
    private val garminSyncService: GarminSyncService = mockk()
    private val garminProperties: GarminProperties =
        mockk {
            every { email } returns "test@example.com"
            every { password } returns "secret"
        }
    private val job = GarminSyncJob(garminSyncService, garminProperties)

    @Test
    fun `authenticateOnStartup skips when session is valid`() {
        every { garminSyncService.hasValidSession() } returns true

        job.authenticateOnStartup()

        verify(exactly = 0) { garminSyncService.authenticate(any(), any()) }
    }

    @Test
    fun `authenticateOnStartup authenticates when session is missing`() {
        every { garminSyncService.hasValidSession() } returns false
        justRun { garminSyncService.authenticate("test@example.com", "secret") }

        job.authenticateOnStartup()

        verify(exactly = 1) { garminSyncService.authenticate("test@example.com", "secret") }
    }

    @Test
    fun `authenticateOnStartup does not throw when auth fails`() {
        every { garminSyncService.hasValidSession() } returns false
        every { garminSyncService.authenticate(any(), any()) } throws RuntimeException("429 Too Many Requests")

        job.authenticateOnStartup() // must not throw
    }

    @Test
    fun `syncActivities catches exception and does not propagate`() {
        every { garminSyncService.syncActivities() } throws RuntimeException("connection timeout")

        job.syncActivities() // must not throw
    }
}
