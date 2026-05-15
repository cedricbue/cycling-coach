package com.cyclingcoach.coaching

import com.cyclingcoach.generated.api.CoachingApi
import com.cyclingcoach.generated.model.CoachingRequest
import com.cyclingcoach.generated.model.CoachingResponse
import com.cyclingcoach.generated.model.DailyRecommendation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class CoachingController(
    private val coachingService: CoachingService,
) : CoachingApi {

    override fun getDailyRecommendation(
        lat: Double?,
        lon: Double?,
        regenerate: Boolean,
    ): ResponseEntity<DailyRecommendation> =
        ResponseEntity.ok(coachingService.getDailyRecommendation(lat, lon, regenerate))

    override fun analyzeRide(coachingRequest: CoachingRequest): ResponseEntity<CoachingResponse> {
        // Per-ride AI analysis — not yet implemented
        return ResponseEntity.status(501).build()
    }
}
