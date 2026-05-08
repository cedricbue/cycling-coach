package com.cyclingcoach.ride

import com.cyclingcoach.generated.api.RidesApi
import com.cyclingcoach.generated.model.RideDetail
import com.cyclingcoach.generated.model.RidePage
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class RidesController(
    private val rideReadService: RideReadService,
) : RidesApi {

    override fun listRides(page: Int, size: Int): ResponseEntity<RidePage> =
        ResponseEntity.ok(rideReadService.listRides(page, size))

    override fun getRide(id: Long): ResponseEntity<RideDetail> {
        val ride = rideReadService.getRide(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ride)
    }
}
