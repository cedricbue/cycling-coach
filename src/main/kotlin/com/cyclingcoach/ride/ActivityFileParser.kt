package com.cyclingcoach.ride

interface ActivityFileParser {
    fun supports(content: String): Boolean
    fun parse(content: String): RideData
}
