package com.cyclingcoach.sync

import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class SyncControllerTest {
    private val asyncGarminSyncService: AsyncGarminSyncService = mockk()
    private val controller = SyncController(asyncGarminSyncService)

    @Test
    fun `triggerSync returns 202 Accepted and delegates to async service`() {
        justRun { asyncGarminSyncService.syncActivities() }

        val response = controller.triggerSync()

        assertThat(response.statusCode.value()).isEqualTo(202)
        verify(exactly = 1) { asyncGarminSyncService.syncActivities() }
    }
}
