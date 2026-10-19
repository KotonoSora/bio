plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.cyclonedx)
}

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson3.module.kotlin)
    implementation(libs.logstash.encoder)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.springdoc.openapi)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.postgres)
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

// Build contract: `integrationTest` runs against PG 18.6 + Redis 7.4 via Testcontainers.
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations["testImplementation"]) }
}

tasks.named<Test>("integrationTest") {
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
}

// Deterministic main class (Kotlin top-level main compiles to <File>Kt).
springBoot {
    mainClass.set("com.resume.account.AccountServiceApplicationKt")
}
