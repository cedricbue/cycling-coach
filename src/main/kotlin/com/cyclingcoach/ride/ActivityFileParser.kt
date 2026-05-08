package com.cyclingcoach.ride

import com.cyclingcoach.tcx.TcxData

interface ActivityFileParser {
    fun supports(content: String): Boolean
    fun parse(content: String): TcxData
}
