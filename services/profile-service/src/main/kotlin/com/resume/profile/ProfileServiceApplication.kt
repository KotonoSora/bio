package com.resume.profile

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableCaching
@EnableScheduling
@OpenAPIDefinition(info = Info(title = "profile-service", version = "0.1.0"))
class ProfileServiceApplication

fun main(args: Array<String>) {
    runApplication<ProfileServiceApplication>(*args)
}
