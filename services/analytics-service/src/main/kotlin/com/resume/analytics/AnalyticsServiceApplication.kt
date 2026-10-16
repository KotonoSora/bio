package com.resume.analytics

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableCaching
@EnableScheduling
@OpenAPIDefinition(info = Info(title = "analytics-service", version = "0.1.0"))
class AnalyticsServiceApplication

fun main(args: Array<String>) {
    runApplication<AnalyticsServiceApplication>(*args)
}
