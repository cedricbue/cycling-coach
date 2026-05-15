package com.cyclingcoach.weather

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.LocalTime

@Component
class OpenMeteoClient(
    private val openMeteoRestClient: RestClient,
    private val mapper: ObjectMapper,
) : WeatherProvider {

    override fun fetchWeather(lat: Double, lon: Double): WeatherData {
        val url = buildString {
            append("/v1/forecast")
            append("?latitude=$lat&longitude=$lon")
            append("&hourly=temperature_2m,precipitation_probability,windspeed_10m,windgusts_10m")
            append("&daily=sunrise,sunset")
            append("&forecast_days=1&timezone=auto")
        }

        val body = openMeteoRestClient.get().uri(url).retrieve().body(String::class.java)
            ?: throw IllegalStateException("Empty response from Open-Meteo")

        return parse(body)
    }

    private fun parse(json: String): WeatherData {
        val root = mapper.readTree(json)
        val hourly = root.get("hourly")
        val daily = root.get("daily")
        val nowHour = LocalTime.now().hour
        val end = minOf(nowHour + 6, 24)
        val slice = nowHour until end

        return WeatherData(
            date = LocalDate.now(),
            hourlyTemps = slice.map { hourly.get("temperature_2m").get(it).asDouble() },
            precipProbs = slice.map { hourly.get("precipitation_probability").get(it).asInt() },
            windSpeeds = slice.map { hourly.get("windspeed_10m").get(it).asDouble() },
            windGusts = slice.map { hourly.get("windgusts_10m").get(it).asDouble() },
            sunrise = LocalTime.parse(daily.get("sunrise").get(0).asText().substringAfter("T")),
            sunset = LocalTime.parse(daily.get("sunset").get(0).asText().substringAfter("T")),
        )
    }
}
