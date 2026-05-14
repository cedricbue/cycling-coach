package com.cyclingcoach.ride

import com.cyclingcoach.generated.api.RidesApi
import com.cyclingcoach.generated.model.RideDetail
import com.cyclingcoach.generated.model.RidePage
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
internal class RidesController(
    private val rideService: RideService,
) : RidesApi {
    override fun listRides(
        page: Int,
        size: Int,
    ): ResponseEntity<RidePage> = ResponseEntity.ok(rideService.listRides(page, size))

    override fun getRide(id: Long): ResponseEntity<RideDetail> {
        val ride =
            rideService.getRide(id)
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ride)
    }
}
