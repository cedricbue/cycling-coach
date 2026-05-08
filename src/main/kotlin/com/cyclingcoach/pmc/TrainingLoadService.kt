package com.cyclingcoach.pmc

import com.cyclingcoach.config.VIRTUAL_THREAD_EXECUTOR
import com.cyclingcoach.ride.RideCalculatedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TrainingLoadService(private val repository: TrainingLoadRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async(VIRTUAL_THREAD_EXECUTOR)
    @EventListener
    fun onRideCalculated(event: RideCalculatedEvent) {
        recalculateFrom(event.date)
    }

    fun recalculateFrom(startDate: LocalDate) {
        val prior = repository.findByDate(startDate.minusDays(1))
        var ctl = prior?.ctl ?: 0.0
        var atl = prior?.atl ?: 0.0

        val dailyTss = repository.findDailyTssSince(startDate)
        val today = LocalDate.now()

        val alphaCtl = 2.0 / (42 + 1)
        val alphaAtl = 2.0 / (7 + 1)

        var current = startDate
        while (!current.isAfter(today)) {
            val tss = dailyTss[current] ?: 0.0
            val tsb = ctl - atl
            val newCtl = ctl * (1 - alphaCtl) + tss * alphaCtl
            val newAtl = atl * (1 - alphaAtl) + tss * alphaAtl
            repository.upsert(current, tss, newCtl, newAtl, tsb)
            ctl = newCtl
            atl = newAtl
            current = current.plusDays(1)
        }

        log.info(
            "Training load recalculated from {} to {}: CTL={} ATL={} TSB={}",
            startDate, today,
            "%.2f".format(ctl),
            "%.2f".format(atl),
            "%.2f".format(ctl - atl),
        )
    }
}
