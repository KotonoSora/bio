package com.resume.gateway

import com.resume.gateway.proxy.RouteTable
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableCaching
@EnableScheduling
@OpenAPIDefinition(info = Info(title = "gateway", version = "0.1.0"))
class GatewayApplication {

    /** Forwarding table for the closed service map (`GatewayRoutes.SERVICE_HOSTS`). */
    @Bean
    fun routeTable(): RouteTable = RouteTable()
}

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
