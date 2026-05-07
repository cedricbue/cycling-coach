package com.cyclingcoach.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class JooqConfig {
    // Spring Boot auto-configures DSLContext from spring.datasource + spring.jooq.sql-dialect=SQLITE.
    // Ensure the data directory exists before the datasource initialises.
    @PostConstruct
    fun ensureDataDirectory() {
        File("./data").mkdirs()
    }
}
