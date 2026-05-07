package com.cyclingcoach.activity

data class ActivityInput(
    val externalId: String,
    val name: String?,
    val startTimeGmt: String,
    val rawTcx: String,
)
