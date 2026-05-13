package com.cyclingcoach.pmc

import com.cyclingcoach.generated.api.PmcApi
import com.cyclingcoach.generated.model.PmcDataPoint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
class PmcController(
    private val repository: TrainingLoadRepository,
    private val trainingLoadService: TrainingLoadService,
) : PmcApi {
    override fun getPmc(
        from: LocalDate?,
        to: LocalDate?,
    ): ResponseEntity<List<PmcDataPoint>> {
        trainingLoadService.ensureUpToDate()
        val rows = repository.findBetween(from, to)
        val result =
            rows.map {
                PmcDataPoint(date = it.date, tss = it.tss, ctl = it.ctl, atl = it.atl, tsb = it.tsb)
            }
        return ResponseEntity.ok(result)
    }
}
