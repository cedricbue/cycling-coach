package com.cyclingcoach.pmc

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate

@Tag("unit")
class TrainingLoadServiceTest {
    private val repository: TrainingLoadRepository = mockk()
    private val service = TrainingLoadService(repository)

    private val today = LocalDate.now()

    @Test
    fun `onRideCalculated triggers recalculation from event date`() {
        val date = today.minusDays(1)
        every { repository.findByDate(date.minusDays(1)) } returns null
        every { repository.findDailyTssSince(date) } returns mapOf(date to 50.0)
        justRun { repository.upsert(any(), any(), any(), any(), any()) }

        service.recalculateFrom(date)

        verify { repository.findDailyTssSince(date) }
    }

    @Test
    fun `cold start single 100 TSS ride produces correct CTL and ATL`() {
        val date = today
        every { repository.findByDate(date.minusDays(1)) } returns null
        every { repository.findDailyTssSince(date) } returns mapOf(date to 100.0)

        val upsertArgs = mutableListOf<Triple<Double, Double, Double>>() // tss, ctl, atl
        every { repository.upsert(any(), any(), any(), any(), any()) } answers {
            upsertArgs.add(Triple(secondArg(), thirdArg(), arg(3)))
        }

        service.recalculateFrom(date)

        assertThat(upsertArgs).hasSize(1)
        val (tss, ctl, atl) = upsertArgs[0]
        assertThat(tss).isCloseTo(100.0, Offset.offset(0.001))
        assertThat(ctl).isCloseTo(100.0 / 42.0, Offset.offset(0.001))
        assertThat(atl).isCloseTo(100.0 / 7.0, Offset.offset(0.001))
    }

    @Test
    fun `TSB on day D equals CTL_prev minus ATL_prev`() {
        val date = today
        val prevCtl = 30.0
        val prevAtl = 40.0
        every { repository.findByDate(date.minusDays(1)) } returns
            TrainingLoadRow(date.minusDays(1), 80.0, prevCtl, prevAtl, prevCtl - prevAtl)
        every { repository.findDailyTssSince(date) } returns mapOf(date to 50.0)

        val tsbSlot = slot<Double>()
        val ctlSlot = slot<Double>()
        val atlSlot = slot<Double>()
        every { repository.upsert(eq(date), any(), capture(ctlSlot), capture(atlSlot), capture(tsbSlot)) } returns Unit

        service.recalculateFrom(date)

        assertThat(tsbSlot.captured).isCloseTo(prevCtl - prevAtl, Offset.offset(0.001))
    }

    @Test
    fun `recalculateFrom does not upsert days before startDate`() {
        val startDate = today
        every { repository.findByDate(startDate.minusDays(1)) } returns null
        every { repository.findDailyTssSince(startDate) } returns emptyMap()
        justRun { repository.upsert(any(), any(), any(), any(), any()) }

        service.recalculateFrom(startDate)

        verify(exactly = 0) { repository.upsert(match { it.isBefore(startDate) }, any(), any(), any(), any()) }
    }
}
