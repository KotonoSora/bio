package com.resume.publicsite.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/** DTOs are data classes; Jackson serializes ISO8601 Instants and omits nulls. Unknown fields fail closed. */
@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(builder: Jackson2ObjectMapperBuilder): ObjectMapper =
        builder
            .modules(JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .failOnUnknownProperties(true)
            .build<ObjectMapper>()
}
