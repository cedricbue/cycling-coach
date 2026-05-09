package com.cyclingcoach.garmin

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class GarminControllerTest {
    private val garminSyncService: GarminSyncService = mockk()
    private val controller = GarminController(garminSyncService)

    @Test
    fun `triggerGarminSync returns 202 Accepted and delegates to sync service`() {
        justRun { garminSyncService.syncAll() }

        val response = controller.triggerGarminSync()

        assertThat(response.statusCode.value()).isEqualTo(202)
        verify(exactly = 1) { garminSyncService.syncAll() }
    }
}
