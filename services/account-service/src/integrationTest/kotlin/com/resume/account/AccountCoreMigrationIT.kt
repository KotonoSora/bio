package com.resume.account

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * G1/G2: fresh-apply Flyway replay on PG 18.6 plus the RLS and constraint drills
 * (TL 02 "RLS and access paths"). Fixture rows are seeded through the superuser
 * connection (bypasses RLS); every assertion then runs on the least-privilege
 * app role so the server-set scopes are proven, not assumed.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AccountCoreMigrationIT {

    companion object {
        private const val APP_USER = "account_app"
        private const val APP_PW = "account_app_it"
        private const val MIG_USER = "account_migrator"
        private const val MIG_PW = "account_migrator_it"

        @Container
        @JvmStatic
        val pg: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:18.6@sha256:74935e72241653ca55e0414067e6d8763aceb8a810eb51b452253ec3dcfc4336").asCompatibleSubstituteFor("postgres"))
                .withInitScript("account-it-init.sql")

        private var migrationsExecuted = -1

        @JvmStatic
        @BeforeAll
        fun freshApply() {
            val result = Flyway.configure()
                .dataSource(pg.jdbcUrl, MIG_USER, MIG_PW)
                .locations("classpath:db/migration")
                .load()
                .migrate()
            migrationsExecuted = result.migrationsExecuted
        }
    }

    private fun app(): Connection = DriverManager.getConnection(pg.jdbcUrl, APP_USER, APP_PW)
    private fun root(): Connection = DriverManager.getConnection(pg.jdbcUrl, pg.username, pg.password)

    private fun exec(c: Connection, sql: String) {
        c.createStatement().use { it.execute(sql) }
    }

    private fun count(c: Connection, sql: String): Int =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    private fun scope(c: Connection, userId: String? = null, context: String? = null) {
        exec(c, "RESET app.user_id")
        exec(c, "RESET app.context")
        userId?.let { exec(c, "SELECT set_config('app.user_id', '$it', false)") }
        context?.let { exec(c, "SELECT set_config('app.context', '$it', false)") }
    }

    @Test
    @Order(1)
    fun `fresh apply lands the core schema with forced RLS`() {
        assertTrue(migrationsExecuted >= 1, "V1 applied on an empty database")
        root().use { c ->
            val tables = count(
                c,
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' " +
                    "AND table_name IN ('users', 'linked_accounts', 'sessions', 'audit_log')",
            )
            assertEquals(4, tables)
            val forced = count(
                c,
                "SELECT count(*) FROM pg_class WHERE relnamespace = 'public'::regnamespace " +
                    "AND relname IN ('users', 'linked_accounts', 'sessions', 'audit_log') " +
                    "AND relrowsecurity AND relforcerowsecurity",
            )
            assertEquals(4, forced, "RLS enabled + forced on every core table")
        }
    }

    @Test
    @Order(2)
    fun `seed fixture rows through the superuser connection`() {
        root().use { c ->
            exec(
                c,
                "INSERT INTO users (id, email, email_verified) VALUES " +
                    "('usr_it_1', 'one@example.test', TRUE), ('usr_it_2', 'two@example.test', TRUE)",
            )
            exec(c, "UPDATE users SET contact_email = 'A@X.TEST', contact_verified_at = now() WHERE id = 'usr_it_2'")
            exec(
                c,
                "INSERT INTO linked_accounts (id, user_id, provider_id, provider_sub) VALUES " +
                    "('lnk_it_1', 'usr_it_1', 'google', 'sub-1'), ('lnk_it_2', 'usr_it_2', 'google', 'sub-2')",
            )
            exec(
                c,
                "INSERT INTO sessions (sid, user_id, created_at, last_active_at, auth_time, idle_expires_at, absolute_expires_at) VALUES " +
                    "('sid_it_1', 'usr_it_1', now(), now(), now(), now() + interval '7 days', now() + interval '90 days'), " +
                    "('sid_it_2', 'usr_it_2', now(), now(), now(), now() + interval '7 days', now() + interval '90 days')",
            )
            exec(c, "INSERT INTO audit_log (id, user_id, action) VALUES ('aud_it_1', 'usr_it_1', 'login')")
            assertEquals(2, count(c, "SELECT count(*) FROM users"))
        }
    }

    @Test
    @Order(3)
    fun `unset scope denies by default and RESET leaves no residual scope`() {
        app().use { c ->
            exec(c, "RESET app.user_id")
            exec(c, "RESET app.context")
            assertEquals(0, count(c, "SELECT count(*) FROM users"))
            assertEquals(0, count(c, "SELECT count(*) FROM sessions"))
            assertEquals(0, count(c, "SELECT count(*) FROM audit_log"))
        }
    }

    @Test
    @Order(4)
    fun `owner scope sees exactly the request user rows`() {
        app().use { c ->
            scope(c, userId = "usr_it_1")
            assertEquals(1, count(c, "SELECT count(*) FROM users"))
            assertEquals(1, count(c, "SELECT count(*) FROM sessions WHERE sid = 'sid_it_1'"))
            assertEquals(0, count(c, "SELECT count(*) FROM sessions WHERE sid = 'sid_it_2'"))
            assertEquals(1, count(c, "SELECT count(*) FROM linked_accounts"))
        }
    }

    @Test
    @Order(5)
    fun `owner scope cannot insert or update foreign rows`() {
        app().use { c ->
            scope(c, userId = "usr_it_1")
            val insert = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO users (id, email) VALUES ('usr_evil', 'evil@example.test')")
            }
            assertEquals("42501", insert.sqlState, "WITH CHECK blocks foreign insert")
            exec(c, "UPDATE sessions SET device_label = 'x' WHERE sid = 'sid_it_2'")
            assertEquals(0, count(c, "SELECT count(*) FROM sessions WHERE device_label = 'x'"), "foreign rows are invisible")
        }
    }

    @Test
    @Order(6)
    fun `svc context reads across accounts for internal handlers`() {
        app().use { c ->
            scope(c, context = "svc")
            assertEquals(2, count(c, "SELECT count(*) FROM users"))
            assertEquals(2, count(c, "SELECT count(*) FROM sessions"))
        }
    }

    @Test
    @Order(7)
    fun `audit rows are append-only for the app role`() {
        app().use { c ->
            scope(c, userId = "usr_it_1")
            exec(c, "INSERT INTO audit_log (id, user_id, action) VALUES ('aud_it_2', 'usr_it_1', 'session_revoke')")
            exec(c, "UPDATE audit_log SET target = 'x' WHERE id = 'aud_it_1'")
            exec(c, "DELETE FROM audit_log WHERE id = 'aud_it_1'")
            assertEquals(0, count(c, "SELECT count(*) FROM audit_log WHERE target = 'x'"), "no UPDATE policy")
            assertEquals(2, count(c, "SELECT count(*) FROM audit_log"), "no DELETE policy")
        }
    }

    @Test
    @Order(8)
    fun `constraint probes hold`() {
        root().use { c ->
            val dupLink = assertThrows(SQLException::class.java) {
                exec(
                    c,
                    "INSERT INTO linked_accounts (id, user_id, provider_id, provider_sub) VALUES " +
                        "('lnk_dup', 'usr_it_2', 'google', 'sub-1')",
                )
            }
            assertEquals("23505", dupLink.sqlState, "provider subject linked once")

            val dupContact = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO users (id, contact_email, contact_verified_at) VALUES ('usr_it_3', 'a@x.test', now())")
            }
            assertEquals("23505", dupContact.sqlState, "verified contact email unique case-insensitively")

            exec(c, "INSERT INTO users (id, contact_email) VALUES ('usr_it_4', 'a@x.test')")
            assertEquals(1, count(c, "SELECT count(*) FROM users WHERE id = 'usr_it_4'"), "unverified contact email stays allowed")
        }
    }

    @Test
    @Order(9)
    fun `deleting an account cascades links and sessions`() {
        root().use { c ->
            exec(c, "DELETE FROM users WHERE id = 'usr_it_1'")
            assertEquals(0, count(c, "SELECT count(*) FROM linked_accounts WHERE user_id = 'usr_it_1'"))
            assertEquals(0, count(c, "SELECT count(*) FROM sessions WHERE user_id = 'usr_it_1'"))
        }
    }
}
