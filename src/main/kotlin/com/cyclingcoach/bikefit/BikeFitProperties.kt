package com.cyclingcoach.bikefit

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "bike-fit")
data class BikeFitProperties(
    var dataDir: String = "./data/bike-fit",
    var landmarksApiUrl: String = "http://0.0.0.0:8002",
)
