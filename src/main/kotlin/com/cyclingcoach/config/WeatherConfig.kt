package com.cyclingcoach.config

import com.cyclingcoach.weather.WeatherProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class WeatherConfig(
    private val weatherProperties: WeatherProperties,
) {
    @Bean
    fun openMeteoRestClient(): RestClient = RestClient.builder()
        .baseUrl(weatherProperties.openMeteo.baseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(10))
        })
        .build()
}
