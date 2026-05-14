package com.cyclingcoach.pmc

import com.cyclingcoach.generated.model.PmcDataPoint
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
internal class PmcService(
    private val trainingLoadService: TrainingLoadService,
) {
    fun getPmc(
        from: LocalDate?,
        to: LocalDate?,
    ): List<PmcDataPoint> {
        trainingLoadService.ensureUpToDate()
        return trainingLoadService.findBetween(from, to).map {
            PmcDataPoint(date = it.date, tss = it.tss, ctl = it.ctl, atl = it.atl, tsb = it.tsb)
        }
    }
}
