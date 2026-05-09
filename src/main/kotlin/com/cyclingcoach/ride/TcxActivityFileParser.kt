package com.cyclingcoach.ride

import com.cyclingcoach.tcx.TcxData
import com.cyclingcoach.tcx.TcxParser
import org.springframework.stereotype.Component

@Component
class TcxActivityFileParser : ActivityFileParser {
    private val parser = TcxParser()
    override fun supports(content: String): Boolean = parser.supports(content)
    override fun parse(content: String): TcxData = parser.parse(content)
}
