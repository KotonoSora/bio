package com.resume.shop.domain

import org.springframework.boot.flyway.autoconfigure.FlywayProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Append-only audit log (actor, action, target, result), 24-month retention.
 * Appends ride the app role (RLS-scoped to the request user); the retention
 * purge is ops-owned and runs on the migrator role because RLS deliberately
 * grants the app role no DELETE. Never reused for business logic.
 */
@Service
class AuditService(flyway: FlywayProperties, private val appDb: JdbcTemplate) {

    private val migratorDb = JdbcTemplate(
        DriverManagerDataSource(
            requireNotNull(flyway.url) { "spring.flyway.url is required (migrator role)" },
            requireNotNull(flyway.getUser()) { "spring.flyway.user is required (migrator role)" },
            requireNotNull(flyway.getPassword()) { "spring.flyway.password is required" },
        ),
    )

    fun append(userId: String, actor: String, action: String, target: String, result: String) {
        appDb.update(
            "INSERT INTO audit_log (id, user_id, actor, action, target, result) VALUES (?, ?, ?, ?, ?, ?)",
            "adt_" + UUID.randomUUID().toString().take(12), userId, actor, action, target, result,
        )
    }

    @Scheduled(cron = "0 0 3 1 * *")
    fun purgeExpired() {
        migratorDb.update("DELETE FROM audit_log WHERE created_at < now() - INTERVAL '24 months'")
    }
}
