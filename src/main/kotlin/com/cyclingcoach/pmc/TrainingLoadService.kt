package com.cyclingcoach.pmc

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class TrainingLoadService(
    private val trainingLoadRepository: TrainingLoadRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ensureUpToDate() {
        val latest = trainingLoadRepository.findLatestDate() ?: return
        val tomorrow = latest.plusDays(1)
        if (!tomorrow.isAfter(LocalDate.now())) {
            recalculateFrom(tomorrow)
        }
    }

    fun findBetween(
        from: LocalDate?,
        to: LocalDate?,
    ): List<TrainingLoadRow> = trainingLoadRepository.findBetween(from, to)

    fun recalculateFrom(startDate: LocalDate) {
        val prior = trainingLoadRepository.findByDate(startDate.minusDays(1))
        var ctl = prior?.ctl ?: 0.0
        var atl = prior?.atl ?: 0.0
        val dailyTss = trainingLoadRepository.findDailyTssSince(startDate)
        val today = LocalDate.now()

        if (dailyTss.isEmpty()) {
            log.warn("No TSS data found since {} — training load will be zero. Is FTP configured?", startDate)
        }

        val alphaCtl = 1.0 / 42.0
        val alphaAtl = 1.0 / 7.0

        var current = startDate
        while (!current.isAfter(today)) {
            val tss = dailyTss[current] ?: 0.0
            val tsb = ctl - atl
            val newCtl = ctl * (1 - alphaCtl) + tss * alphaCtl
            val newAtl = atl * (1 - alphaAtl) + tss * alphaAtl
            trainingLoadRepository.upsert(current, tss, newCtl, newAtl, tsb)
            ctl = newCtl
            atl = newAtl
            current = current.plusDays(1)
        }

        log.info(
            "Training load recalculated from {} to {}: CTL={} ATL={} TSB={}",
            startDate,
            today,
            "%.2f".format(ctl),
            "%.2f".format(atl),
            "%.2f".format(ctl - atl),
        )
    }
}
