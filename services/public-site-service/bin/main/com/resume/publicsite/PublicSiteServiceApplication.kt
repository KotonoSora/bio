package com.resume.publicsite

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(info = Info(title = "public-site-service", version = "0.1.0"))
class PublicSiteServiceApplication

fun main(args: Array<String>) {
    runApplication<PublicSiteServiceApplication>(*args)
}
