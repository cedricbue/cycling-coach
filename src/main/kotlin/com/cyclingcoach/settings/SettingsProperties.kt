package com.cyclingcoach.settings

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "cycling")
data class SettingsProperties(
    var zones: ZonesProperties = ZonesProperties(),
) {
    data class ZonesProperties(
        var power: PowerZoneProperties = PowerZoneProperties(),
        var hr: HrZoneProperties = HrZoneProperties(),
    )

    data class PowerZoneProperties(
        var z1Max: Int = 55,
        var z2Max: Int = 75,
        var z3Max: Int = 90,
        var z4Max: Int = 105,
        var z5Max: Int = 120,
    )

    data class HrZoneProperties(
        var z1Max: Int = 68,
        var z2Max: Int = 83,
        var z3Max: Int = 94,
        var z4Max: Int = 105,
    )
}
