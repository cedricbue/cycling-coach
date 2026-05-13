package com.cyclingcoach.pmc

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.cyclingcoach.generated.jooq.tables.references.TRAINING_LOAD
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Verifies that GET /api/pmc (via [PmcController]) triggers [TrainingLoadService.ensureUpToDate]:
 * when the training_load table has rows up to yesterday but not today, the controller call
 * must extend the EWMA chain through today (TSS=0 rest day) and include today in the response.
 */
@Tag("integration")
class PmcControllerIntegrationTest : AbstractApplicationIntegrationTest() {
    @Autowired
    private lateinit var pmcController: PmcController

    @Autowired
    private lateinit var trainingLoadRepository: TrainingLoadRepository

    @Test
    fun `getPmc extends training load through today on a rest day and returns today in response`() {
        val yesterday = LocalDate.now().minusDays(1)
        val today = LocalDate.now()

        // Seed a realistic CTL/ATL state up to yesterday — simulates a ride 2 days ago
        // that pushed ATL up, with no ride yesterday or today.
        val seedCtl = 40.0
        val seedAtl = 55.0
        dsl
            .insertInto(TRAINING_LOAD)
            .set(TRAINING_LOAD.DATE, yesterday.toString())
            .set(TRAINING_LOAD.TSS, 0f)
            .set(TRAINING_LOAD.CTL, seedCtl.toFloat())
            .set(TRAINING_LOAD.ATL, seedAtl.toFloat())
            .set(TRAINING_LOAD.TSB, (seedCtl - seedAtl).toFloat())
            .execute()

        // Precondition: no row for today
        assertThat(trainingLoadRepository.findByDate(today)).isNull()

        // Act — call through the real controller, which calls ensureUpToDate()
        val response = pmcController.getPmc(null, null)

        // Today's row must now exist in the DB
        val todayRow = trainingLoadRepository.findByDate(today)
        assertThat(todayRow).isNotNull()

        // EWMA decay with TSS=0: CTL_today = CTL_yesterday × (1 - 1/42), ATL_today = ATL_yesterday × (1 - 1/7)
        val expectedCtl = seedCtl * (1 - 1.0 / 42.0)
        val expectedAtl = seedAtl * (1 - 1.0 / 7.0)
        val expectedTsb = seedCtl - seedAtl  // TSB uses prior-day values

        assertThat(todayRow!!.ctl).isCloseTo(expectedCtl, Offset.offset(0.01))
        assertThat(todayRow.atl).isCloseTo(expectedAtl, Offset.offset(0.01))
        assertThat(todayRow.tsb).isCloseTo(expectedTsb, Offset.offset(0.01))
        assertThat(todayRow.tss).isCloseTo(0.0, Offset.offset(0.001))

        // Today must appear in the response body
        val body = response.body!!
        assertThat(body.any { it.date == today }).isTrue()
        val todayPoint = body.first { it.date == today }
        assertThat(todayPoint.ctl).isCloseTo(expectedCtl, Offset.offset(0.01))
        assertThat(todayPoint.atl).isCloseTo(expectedAtl, Offset.offset(0.01))
    }

    @Test
    fun `getPmc is idempotent when today already exists`() {
        val today = LocalDate.now()

        // Seed today's row — simulates a ride already synced today
        dsl
            .insertInto(TRAINING_LOAD)
            .set(TRAINING_LOAD.DATE, today.toString())
            .set(TRAINING_LOAD.TSS, 80f)
            .set(TRAINING_LOAD.CTL, 42f)
            .set(TRAINING_LOAD.ATL, 50f)
            .set(TRAINING_LOAD.TSB, -8f)
            .execute()

        pmcController.getPmc(null, null)

        // Row must remain unchanged — ensureUpToDate() is a no-op when today already exists
        val row = trainingLoadRepository.findByDate(today)!!
        assertThat(row.tss).isCloseTo(80.0, Offset.offset(0.001))
        assertThat(row.ctl).isCloseTo(42.0, Offset.offset(0.001))
        assertThat(row.atl).isCloseTo(50.0, Offset.offset(0.001))
    }

    @Test
    fun `getPmc returns empty list and does not crash when no training load exists`() {
        // Empty DB — ensureUpToDate() finds no latest date and returns immediately
        val response = pmcController.getPmc(null, null)
        assertThat(response.body).isEmpty()
    }
}
