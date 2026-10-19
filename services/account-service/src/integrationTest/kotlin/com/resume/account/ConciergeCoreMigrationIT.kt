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
 * Feature 05 (concierge) schema drills on PG 18.6: V3 applies on the V1/V2
 * baseline, every business table carries user_id + index + forced RLS, the
 * owner/svc scopes behave, and the approvals ledger is append-only at the
 * database too (TL 02 "RLS and access paths"; BA 05).
 *
 * Fixtures are seeded through the superuser connection (bypasses RLS); every
 * assertion then runs on the least-privilege app role.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ConciergeCoreMigrationIT {

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
    fun `fresh apply lands the concierge schema with forced RLS`() {
        assertTrue(migrationsExecuted >= 3, "V3 applies on the V1/V2 baseline")
        root().use { c ->
            val tables = count(
                c,
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' " +
                    "AND table_name IN ('concierge_sow_templates', 'concierge_staff', " +
                    "'concierge_contracts', 'concierge_tasks', 'concierge_approvals')",
            )
            assertEquals(5, tables)
            val forced = count(
                c,
                "SELECT count(*) FROM pg_class WHERE relnamespace = 'public'::regnamespace " +
                    "AND relname IN ('concierge_sow_templates', 'concierge_staff', " +
                    "'concierge_contracts', 'concierge_tasks', 'concierge_approvals') " +
                    "AND relrowsecurity AND relforcerowsecurity",
            )
            assertEquals(5, forced, "RLS enabled + forced on every concierge table")
            val indexes = count(
                c,
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname IN " +
                    "('idx_concierge_contracts_user', 'idx_concierge_tasks_user', " +
                    "'idx_concierge_approvals_user', 'idx_concierge_contracts_live')",
            )
            assertEquals(4, indexes, "user_id indexes + the one-live-contract partial unique index")
        }
    }

    @Test
    @Order(2)
    fun `seed fixture rows through the superuser connection`() {
        root().use { c ->
            exec(c, "INSERT INTO concierge_sow_templates (id, name, menu) VALUES ('cnc_tpl_it_1', 'Starter', '[\"setup\"]'::jsonb)")
            exec(c, "INSERT INTO concierge_staff (id, user_id, role, name) VALUES ('cnc_staff_it_1', 'usr_agency_1', 'owner', 'Agent')")
            exec(
                c,
                "INSERT INTO concierge_contracts (id, user_id, status, sla_tier, sow) VALUES " +
                    "('cnc_it_1', 'usr_cnc_1', 'active', 'rapid', '[{\"kind\":\"campaign\",\"volume\":2}]'::jsonb), " +
                    "('cnc_it_2', 'usr_cnc_2', 'active', 'standard', '[]'::jsonb)",
            )
            exec(
                c,
                "INSERT INTO concierge_tasks (id, contract_id, user_id, kind, status, origin) VALUES " +
                    "('cnc_task_it_1', 'cnc_it_1', 'usr_cnc_1', 'campaign', 'requested', 'client'), " +
                    "('cnc_task_it_2', 'cnc_it_2', 'usr_cnc_2', 'report', 'approved', 'client')",
            )
            exec(
                c,
                "INSERT INTO concierge_approvals (id, contract_id, task_id, user_id, scope, channel, approved_by) VALUES " +
                    "('cnc_apr_it_1', 'cnc_it_1', 'cnc_task_it_1', 'usr_cnc_1', 'Run it', 'dashboard', 'usr_cnc_1')",
            )
            assertEquals(2, count(c, "SELECT count(*) FROM concierge_contracts"))
        }
    }

    @Test
    @Order(3)
    fun `unset scope denies by default`() {
        app().use { c ->
            exec(c, "RESET app.user_id")
            exec(c, "RESET app.context")
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_contracts"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_tasks"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_approvals"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_sow_templates"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_staff"))
        }
    }

    @Test
    @Order(4)
    fun `owner scope sees exactly the request user rows`() {
        app().use { c ->
            scope(c, userId = "usr_cnc_1")
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_contracts WHERE id = 'cnc_it_1'"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_contracts WHERE id = 'cnc_it_2'"), "foreign contract invisible")
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_tasks"))
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_approvals"))
            exec(c, "INSERT INTO concierge_approvals (id, contract_id, task_id, user_id, scope, channel, approved_by) " +
                "VALUES ('cnc_apr_it_2', 'cnc_it_1', 'cnc_task_it_1', 'usr_cnc_1', 'Own insert', 'dashboard', 'usr_cnc_1')")
            assertEquals(2, count(c, "SELECT count(*) FROM concierge_approvals"), "dashboard approval appends")
        }
    }

    @Test
    @Order(5)
    fun `owner scope cannot insert or update foreign rows`() {
        app().use { c ->
            scope(c, userId = "usr_cnc_1")
            val foreignInsert = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO concierge_contracts (id, user_id, status, sla_tier) VALUES ('cnc_evil', 'usr_cnc_2', 'draft', 'rapid')")
            }
            assertEquals("42501", foreignInsert.sqlState, "WITH CHECK blocks foreign insert")
            exec(c, "UPDATE concierge_contracts SET status = 'paused' WHERE id = 'cnc_it_2'")
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_contracts WHERE status = 'paused'"), "foreign rows are invisible")
        }
    }

    @Test
    @Order(6)
    fun `templates and staffing stay mesh-only`() {
        app().use { c ->
            scope(c, userId = "usr_agency_1")
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_sow_templates"), "no owner policy on templates")
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_staff"), "the agency account reads its own roster row")
            scope(c, context = "svc")
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_sow_templates"))
            assertEquals(2, count(c, "SELECT count(*) FROM concierge_contracts"))
            assertEquals(2, count(c, "SELECT count(*) FROM concierge_tasks"))
        }
    }

    @Test
    @Order(7)
    fun `approvals are append-only for the app role`() {
        app().use { c ->
            scope(c, context = "svc")
            val update = assertThrows(SQLException::class.java) {
                exec(c, "UPDATE concierge_approvals SET scope = 'edited' WHERE id = 'cnc_apr_it_1'")
            }
            assertEquals("42501", update.sqlState, "UPDATE grant revoked")
            val delete = assertThrows(SQLException::class.java) {
                exec(c, "DELETE FROM concierge_approvals WHERE id = 'cnc_apr_it_1'")
            }
            assertEquals("42501", delete.sqlState, "DELETE grant revoked")
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_approvals WHERE scope = 'edited'"))
            assertEquals(2, count(c, "SELECT count(*) FROM concierge_approvals"))
        }
    }

    @Test
    @Order(8)
    fun `constraint probes hold`() {
        root().use { c ->
            val secondLive = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO concierge_contracts (id, user_id, status, sla_tier) VALUES ('cnc_dup', 'usr_cnc_1', 'paused', 'rapid')")
            }
            assertEquals("23505", secondLive.sqlState, "one live contract per client")

            val badStatus = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO concierge_contracts (id, user_id, status, sla_tier) VALUES ('cnc_bad', 'usr_cnc_9', 'archived', 'rapid')")
            }
            assertEquals("23514", badStatus.sqlState, "status vocabulary is fixed")

            val orphanTask = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO concierge_tasks (id, contract_id, user_id, kind, status, origin) VALUES ('cnc_orphan', 'cnc_missing', 'usr_cnc_1', 'report', 'requested', 'client')")
            }
            assertEquals("23503", orphanTask.sqlState, "tasks belong to a contract")

            val badChannel = assertThrows(SQLException::class.java) {
                exec(c, "INSERT INTO concierge_approvals (id, contract_id, user_id, scope, channel, approved_by) VALUES ('cnc_bad_apr', 'cnc_it_1', 'usr_cnc_1', 'x', 'sms', 'usr_cnc_1')")
            }
            assertEquals("23514", badChannel.sqlState, "approval channels are dashboard|signed_email")
        }
    }

    @Test
    @Order(9)
    fun `deleting a contract cascades its board and approvals`() {
        root().use { c ->
            exec(c, "DELETE FROM concierge_contracts WHERE id = 'cnc_it_1'")
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_tasks WHERE contract_id = 'cnc_it_1'"))
            assertEquals(0, count(c, "SELECT count(*) FROM concierge_approvals WHERE contract_id = 'cnc_it_1'"))
            assertEquals(1, count(c, "SELECT count(*) FROM concierge_contracts"), "the other client is untouched")
        }
    }
}
