package com.cyclingcoach.user

import com.cyclingcoach.generated.api.WeightApi
import com.cyclingcoach.generated.model.WeightEntry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class WeightController(
    private val repository: WeightRepository,
) : WeightApi {
    override fun getWeightHistory(): ResponseEntity<List<WeightEntry>> {
        val entries =
            repository.findAll().map { row ->
                WeightEntry(
                    id = row.id,
                    date = row.date,
                    weightKg = row.weightKg,
                )
            }
        return ResponseEntity.ok(entries)
    }
}
