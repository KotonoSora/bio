plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.cyclonedx)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.logstash.encoder)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.springdoc.openapi)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.archunit)
}

tasks.named<Test>("integrationTest") {
    useJUnitPlatform()
}

// Deterministic main class (Kotlin top-level main compiles to <File>Kt).
springBoot {
    mainClass.set("com.resume.gateway.GatewayApplicationKt")
}
