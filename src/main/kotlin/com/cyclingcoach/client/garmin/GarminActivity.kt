package com.cyclingcoach.client.garmin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminActivity(
    @JsonProperty("activityId") val activityId: Long,
    @JsonProperty("activityName") val activityName: String?,
    @JsonProperty("startTimeGMT") val startTimeGmt: String?,
    @JsonProperty("activityType") val activityType: GarminActivityType?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GarminActivityType(
    @JsonProperty("typeKey") val typeKey: String?,
)
