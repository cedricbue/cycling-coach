package com.cyclingcoach

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
class CyclingCoachApplication

fun main(args: Array<String>) {
    File("./data").mkdirs()
    runApplication<CyclingCoachApplication>(*args)
}
