# Multi-stage OCI image (04-containers-and-compose.md).
# Build context is the REPO ROOT (reproducible from the tag alone): the wrapper,
# catalog, and all sources ship in; only this service's boot jar ships out.
# Build: Gradle 9.8.1 (digest-pinned). Runtime: Eclipse Temurin JRE 21 (digest-pinned),
# non-root, no build tools, no secrets, no .env baked. SIGTERM -> Spring graceful shutdown.
FROM gradle:9.8.1-jdk21@sha256:d5c815d1281e3edc7e986ca573b1b309639747fa0d91a2199adadf327cfdf634 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY --chown=gradle:gradle gradle/ gradle/
COPY --chown=gradle:gradle gateway/ gateway/
COPY --chown=gradle:gradle services/ services/
# Shared BuildKit cache mount: GRADLE_USER_HOME must match the mount target.
ENV GRADLE_USER_HOME=/home/gradle/.gradle
RUN --mount=type=cache,target=/home/gradle/.gradle ./gradlew :services:billing-service:bootJar --no-daemon -x test -x integrationTest

FROM eclipse-temurin:21-jre@sha256:ccbad9e7cb84c27092b12c40a4143e5733e050db9c3784d84f632dcf423fb2ef AS runtime
RUN useradd -m -u 10001 appuser
USER appuser
WORKDIR /app
COPY --from=build /workspace/services/billing-service/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
