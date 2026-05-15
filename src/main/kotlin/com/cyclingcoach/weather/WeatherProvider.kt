package com.cyclingcoach.weather

interface WeatherProvider {
    fun fetchWeather(lat: Double, lon: Double): WeatherData
}
