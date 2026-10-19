package com.resume.account.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinModule as Kotlin3Module

/**
 * DTOs are data classes; Jackson serializes ISO8601 Instants and omits nulls.
 * Unknown fields fail closed.
 *
 * Two mappers coexist on Boot 4: the legacy Jackson 2 [ObjectMapper] bean
 * (AuthStateStore reads flow state back with it) and the auto-configured
 * Jackson 3 `JsonMapper` Spring MVC binds request bodies with. Each needs its
 * own Kotlin module: without the 3.x one the data-class defaults are not
 * honoured and any body that omits an optional field 422s (`{"active":false}`
 * PATCHes are the mesh contract, not a typo).
 */
@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper = ObjectMapper()
        // Kotlin data classes bind through their primary constructor. Without the
        // Kotlin module this mapper cannot deserialize them (AuthStateStore reads
        // flow state back with it), and failures surface as silent nulls.
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)

    /** MVC request bodies: Jackson 3 mapper + Kotlin defaults for omitted fields. */
    @Bean
    fun jsonMapperCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder -> builder.addModule(Kotlin3Module.Builder().build()) }
}
