package com.cyclingcoach.settings

import com.cyclingcoach.generated.api.SettingsApi
import com.cyclingcoach.generated.model.AppSettings
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SettingsController(
    private val settingsService: SettingsService,
) : SettingsApi {
    override fun getSettings(): ResponseEntity<AppSettings> = ResponseEntity.ok(settingsService.getAppSettings())
}
