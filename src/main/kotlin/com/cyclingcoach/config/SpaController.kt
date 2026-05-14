package com.cyclingcoach.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

@Controller
class SpaController {
    // Paths without a dot are Angular routes, not static assets — forward to index.html.
    // /api/** is matched first by its own controllers and never reaches here.
    @RequestMapping(value = ["/{path:[^\\.]*}", "/{path:[^\\.]*}/**"], method = [RequestMethod.GET])
    fun spa(): String = "forward:/index.html"
}
