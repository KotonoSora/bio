package com.resume.shop.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Build pin enforcement: PostgreSQL 18.6 asserted at boot (and in CI).
 * Fail-closed: the service refuses to serve on the wrong major.
 */
@Configuration
class PostgresVersionAssert {
    @Bean
    fun assertPostgres186(db: JdbcTemplate) = org.springframework.boot.ApplicationRunner {
        val v: String? = db.queryForObject("SELECT version()", String::class.java)
        check(v != null && v.contains("PostgreSQL 18.6")) { "PostgreSQL 18.6 required, got: $v" }
    }
}
