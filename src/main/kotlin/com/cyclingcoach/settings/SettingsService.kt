package com.cyclingcoach.settings

import com.cyclingcoach.generated.model.AppSettings
import com.cyclingcoach.generated.model.HrZoneSettings
import com.cyclingcoach.generated.model.PowerZoneSettings
import com.cyclingcoach.user.UserProfileService
import org.springframework.stereotype.Service

@Service
class SettingsService(
    private val props: SettingsProperties,
    private val userProfileService: UserProfileService,
) {
    fun getAppSettings(): AppSettings {
        val p = props.zones.power
        val h = props.zones.hr

        return AppSettings(
            weightKg = userProfileService.findLatestWeightKg(),
            powerZones = PowerZoneSettings(
                z1Max = p.z1Max,
                z2Max = p.z2Max,
                z3Max = p.z3Max,
                z4Max = p.z4Max,
                z5Max = p.z5Max,
            ),
            hrZones = HrZoneSettings(
                z1Max = h.z1Max,
                z2Max = h.z2Max,
                z3Max = h.z3Max,
                z4Max = h.z4Max,
            ),
        )
    }
}
