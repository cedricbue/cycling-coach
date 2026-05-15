package com.cyclingcoach.weather

import com.cyclingcoach.AbstractApplicationIntegrationTest
import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestClientException

class OpenMeteoClientIntegrationTest : AbstractApplicationIntegrationTest() {

    @Autowired
    private lateinit var weatherProvider: WeatherProvider

    @Test
    fun `fetchWeather parses hourly slice and daily sunrise and sunset`() {
        stubForecast()

        val weather = weatherProvider.fetchWeather(47.376, 8.541)

        assertThat(weather.hourlyTemps).isNotEmpty
        assertThat(weather.precipProbs).isNotEmpty
        assertThat(weather.windSpeeds).isNotEmpty
        assertThat(weather.windGusts).isNotEmpty
        // All temps in the stub are in the 15–25 °C range
        assertThat(weather.minTemp).isBetween(14.0, 26.0)
        assertThat(weather.maxTemp).isBetween(14.0, 26.0)
        assertThat(weather.maxPrecipProb).isBetween(0, 100)
        assertThat(weather.maxWindGust).isGreaterThanOrEqualTo(0.0)
        assertThat(weather.sunrise.hour).isBetween(4, 8)
        assertThat(weather.sunset.hour).isBetween(18, 22)
    }

    @Test
    fun `fetchWeather sends correct query parameters`() {
        stubForecast()

        weatherProvider.fetchWeather(47.376, 8.541)

        openMeteoWireMock.verify(
            WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/forecast"))
                .withQueryParam("latitude", WireMock.equalTo("47.376"))
                .withQueryParam("longitude", WireMock.equalTo("8.541"))
                .withQueryParam("hourly", WireMock.containing("temperature_2m"))
                .withQueryParam("hourly", WireMock.containing("precipitation_probability"))
                .withQueryParam("hourly", WireMock.containing("windspeed_10m"))
                .withQueryParam("hourly", WireMock.containing("windgusts_10m"))
                .withQueryParam("daily", WireMock.containing("sunrise"))
                .withQueryParam("daily", WireMock.containing("sunset"))
                .withQueryParam("forecast_days", WireMock.equalTo("1")),
        )
    }

    @Test
    fun `fetchWeather throws when response body is empty`() {
        openMeteoWireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/v1/forecast"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("")),
        )

        assertThatThrownBy { weatherProvider.fetchWeather(47.376, 8.541) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Empty response")
    }

    @Test
    fun `fetchWeather propagates HTTP error as RestClientException`() {
        openMeteoWireMock.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/v1/forecast"))
                .willReturn(WireMock.aResponse().withStatus(503).withBody("Service Unavailable")),
        )

        assertThatThrownBy { weatherProvider.fetchWeather(47.376, 8.541) }
            .isInstanceOf(RestClientException::class.java)
    }
}
