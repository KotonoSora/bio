package com.resume.analytics.config

import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource

/**
 * Read/write split (07-scale-out.md). Read-only transactions route to the replica
 * ONLY when a replica is configured AND measured lag is within the 5 s bound;
 * anything else (writes, unknown lag, exceeded bound) fails back to primary
 * instead of serving stale data. Session/money writes never route: they are not
 * read-only. Without REPLICA_DATASOURCE_URL this is a pass-through to primary.
 */
class LagAwareRouter(
    primary: DataSource,
    replica: DataSource?,
    private val lagSeconds: AtomicReference<Double>,
) : AbstractRoutingDataSource() {
    private val hasReplica = replica != null

    init {
        val targets = mutableMapOf<Any, Any>("primary" to primary)
        if (replica != null) targets["replica"] = replica
        setTargetDataSources(targets)
        setDefaultTargetDataSource(primary)
        afterPropertiesSet()
    }

    override fun determineCurrentLookupKey(): Any? {
        val lag = lagSeconds.get()
        return if (hasReplica && TransactionSynchronizationManager.isCurrentTransactionReadOnly() && lag <= 5.0) {
            "replica"
        } else {
            "primary"
        }
    }
}

@Configuration
class ReplicaRoutingConfig(
    @Value("\${app.replica.datasource.url:}") private val replicaUrl: String,
    @Value("\${app.replica.datasource.username:analytics_app}") private val replicaUser: String,
    @Value("\${app.replica.datasource.password:analytics_app_dev_only}") private val replicaPassword: String,
    meters: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val lagSeconds = AtomicReference(Double.MAX_VALUE)
    private var replicaJdbc: JdbcTemplate? = null

    init {
        meters.gauge("db.replica.lag.seconds", Tags.of("service", "analytics-service"), lagSeconds) { v -> v.get() }
    }

    @Bean
    @Primary
    fun routingDataSource(primary: DataSource): DataSource {
        val replica = if (replicaUrl.isBlank()) {
            null
        } else {
            val hikari = DataSourceBuilder.create()
                .url(replicaUrl).username(replicaUser).password(replicaPassword)
                .type(HikariDataSource::class.java).build()
            RlsAwareDataSource(hikari).also { replicaJdbc = JdbcTemplate(it) }
        }
        return LagAwareRouter(primary, replica, lagSeconds)
    }

    /** Replica lag is a first-class metric; exceeding the bound stops read routing. */
    @Scheduled(fixedDelay = 10_000)
    fun probeLag() {
        val jdbc = replicaJdbc
        if (jdbc == null) {
            lagSeconds.set(Double.MAX_VALUE)
            return
        }
        try {
            // NULL when not in recovery (e.g. pointed at a primary): unbounded, no routing.
            val lag: Double? = jdbc.queryForObject(
                "SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))",
                Double::class.java,
            )
            lagSeconds.set(lag ?: Double.MAX_VALUE)
            if (lag != null && lag > 5.0) log.warn("replica lag exceeded bound: {}s", lag)
        } catch (ex: Exception) {
            lagSeconds.set(Double.MAX_VALUE)
            log.warn("replica lag probe degraded: {}", ex.message)
        }
    }

    @PostConstruct
    fun note() {
        if (replicaUrl.isBlank()) log.info("read replica not configured; primary-only")
    }
}
