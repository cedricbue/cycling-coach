package com.cyclingcoach.weather

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "weather")
data class WeatherProperties(
    var openMeteo: OpenMeteo = OpenMeteo(),
) {
    data class OpenMeteo(
        var baseUrl: String = "https://api.open-meteo.com",
    )
}
