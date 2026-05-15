package com.cyclingcoach.coaching

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "coaching")
data class CoachingProperties(
    var location: Location = Location(),
) {
    data class Location(
        var lat: Double? = null,
        var lon: Double? = null,
    )
}
