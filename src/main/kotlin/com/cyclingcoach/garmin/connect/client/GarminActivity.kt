package com.cyclingcoach.garmin.connect.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Full Garmin Connect activity record as returned by the activity list endpoint.
 * Used both during sync (type-based filtering, event metadata) and by the ride domain
 * (metrics ingestion from pre-calculated fields).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminActivity(
    @JsonProperty("activityId") val activityId: Long,
    @JsonProperty("activityName") val activityName: String?,
    @JsonProperty("startTimeGMT") val startTimeGmt: String?,
    @JsonProperty("activityType") val activityType: GarminActivityType?,
    @JsonProperty("manufacturer") val manufacturer: String?,
    @JsonProperty("distance") val distance: Double?,
    @JsonProperty("duration") val duration: Double?,
    @JsonProperty("elevationGain") val elevationGain: Double?,
    @JsonProperty("elevationLoss") val elevationLoss: Double?,
    @JsonProperty("averageSpeed") val averageSpeed: Double?,
    @JsonProperty("maxSpeed") val maxSpeed: Double?,
    @JsonProperty("averageHR") val averageHR: Double?,
    @JsonProperty("maxHR") val maxHR: Double?,
    @JsonProperty("averageBikingCadenceInRevPerMinute") val averageCadence: Double?,
    @JsonProperty("maxBikingCadenceInRevPerMinute") val maxCadence: Double?,
    @JsonProperty("avgPower") val avgPower: Double?,
    @JsonProperty("maxPower") val maxPower: Double?,
    @JsonProperty("normPower") val normPower: Double?,
    @JsonProperty("maxAvgPower_5") val maxAvgPower5s: Double?,
    @JsonProperty("maxAvgPower_30") val maxAvgPower30s: Double?,
    @JsonProperty("maxAvgPower_60") val maxAvgPower1min: Double?,
    @JsonProperty("maxAvgPower_300") val maxAvgPower5min: Double?,
    @JsonProperty("maxAvgPower_600") val maxAvgPower10min: Double?,
    @JsonProperty("maxAvgPower_1200") val maxAvgPower20min: Double?,
    @JsonProperty("maxAvgPower_3600") val maxAvgPower60min: Double?,
) {
    /** Returns true if this activity is a cycling/biking ride. */
    fun isRide(): Boolean {
        val pid = activityType?.parentTypeId
        if (pid != null) return pid == CYCLING_PARENT_TYPE_ID
        val key = activityType?.typeKey?.lowercase() ?: return false
        return RIDE_TYPE_KEYS.any { key.contains(it) }
    }

    companion object {
        private const val CYCLING_PARENT_TYPE_ID = 2
        private val RIDE_TYPE_KEYS = listOf("cycling", "biking", "ride")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminActivityType(
    @JsonProperty("typeKey") val typeKey: String?,
    @JsonProperty("parentTypeId") val parentTypeId: Int? = null,
)
