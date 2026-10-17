package com.resume.link

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Release gate G1/G2: integration tests run against PG 18.6 (digest-pinned).
 * Fresh-apply Flyway replay and upgrade replay ride on the same image.
 */
@Testcontainers
class PostgresBaselineIT {

    companion object {
        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336").asCompatibleSubstituteFor("postgres"))
    }

    @Test
    fun `pg 18_6 serves SELECT version`() {
        pg.createConnection("").use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT version()").use { rs ->
                    rs.next()
                    assertTrue(rs.getString(1).contains("PostgreSQL 18.6"), rs.getString(1))
                }
            }
        }
    }
}
