package com.cyclingcoach.garmin.connect.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Single weight entry from the Garmin weight-service `/dateRange` endpoint.
 * Each entry represents one weigh-in on a given day.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminWeightEntry(
    @JsonProperty("samplePk") val samplePk: Long?,
    @JsonProperty("calendarDate") val calendarDate: String?,
    @JsonProperty("date") val dateEpochMs: Long?,
    @JsonProperty("weight") val weightGrams: Double?,
    @JsonProperty("bmi") val bmi: Double?,
    @JsonProperty("bodyFat") val bodyFatPercentage: Double?,
    @JsonProperty("bodyWater") val bodyWater: Double?,
    @JsonProperty("muscleMass") val muscleMassGrams: Double?,
    @JsonProperty("boneMass") val boneMassGrams: Double?,
    @JsonProperty("sourceType") val sourceType: String?,
    @JsonProperty("timestampGMT") val timestampGmt: Long?,
) {
    /** Weight in kilograms (Garmin returns grams). */
    fun weightKg(): Double? = weightGrams?.div(1000.0)
}

/**
 * Top-level response from GET /weight-service/weight/dateRange.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminWeightResponse(
    @JsonProperty("startDate") val startDate: String?,
    @JsonProperty("endDate") val endDate: String?,
    @JsonProperty("dateWeightList") val dateWeightList: List<GarminWeightEntry>?,
)
