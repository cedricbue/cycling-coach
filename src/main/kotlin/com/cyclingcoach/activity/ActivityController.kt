package com.cyclingcoach.activity

import com.cyclingcoach.generated.api.ActivitiesApi
import org.springframework.web.bind.annotation.RestController

@RestController
class ActivityController(
    private val activityService: ActivityService
) : ActivitiesApi
