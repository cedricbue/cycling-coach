package com.cyclingcoach.pmc

import com.cyclingcoach.generated.api.PmcApi
import com.cyclingcoach.generated.model.PmcDataPoint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
internal class PmcController(
    private val pmcService: PmcService,
) : PmcApi {
    override fun getPmc(
        from: LocalDate?,
        to: LocalDate?,
    ): ResponseEntity<List<PmcDataPoint>> = ResponseEntity.ok(pmcService.getPmc(from, to))
}
