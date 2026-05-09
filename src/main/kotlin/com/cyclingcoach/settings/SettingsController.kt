package com.cyclingcoach.settings

import com.cyclingcoach.generated.api.SettingsApi
import com.cyclingcoach.generated.model.AppSettings
import com.cyclingcoach.generated.model.HrZoneSettings
import com.cyclingcoach.generated.model.PowerZoneSettings
import com.cyclingcoach.user.UserProfileService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SettingsController(
    private val props: SettingsProperties,
    private val userProfileService: UserProfileService,
) : SettingsApi {

    override fun getSettings(): ResponseEntity<AppSettings> {
        val weightKg = userProfileService.findLatestWeightKg()

        val p = props.zones.power
        val h = props.zones.hr

        val settings = AppSettings(
            weightKg = weightKg,
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

        return ResponseEntity.ok(settings)
    }
}
