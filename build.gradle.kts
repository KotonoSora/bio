import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.cyclonedx) apply false
}

subprojects {
    // Aggregator projects (e.g. :services) carry no code and get no toolchain.
    if (!file("src").exists()) return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.cyclonedx.bom")

    group = "com.resume.platform"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll("-Xjsr305=strict", "-java-parameters")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Integration tests run against PG 18.6 + Redis 7.4 via Testcontainers.
    // `integrationTest` source set is registered per service; empty by default on E0.
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests with Testcontainers (PG 18.6, Redis 7.4)."
        group = "verification"
        useJUnitPlatform()
        shouldRunAfter("test")
    }

    tasks.named("check") {
        dependsOn("integrationTest")
    }

    // Build contract: SBOM artifact archived per tag (`cyclonedxSbom`).
    tasks.register("cyclonedxSbom") {
        description = "Alias for the CycloneDX SBOM gate artifact."
        group = "verification"
        dependsOn("cyclonedxBom")
    }

    // Reproducible archives: fixed file order, timestamps off.
    tasks.withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
