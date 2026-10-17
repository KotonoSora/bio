package com.resume.account.config

import com.resume.account.security.RlsContext
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DelegatingDataSource
import javax.sql.DataSource
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

/**
 * Every borrowed connection is RESET then SET to the server-set request scope, so
 * RLS policies (`app.user_id`, `app.context`) hold on pooled connections.
 * Unset scope = deny-by-default. Pooled state can never leak across requests:
 * reset runs before every set.
 */
class RlsAwareDataSource(delegate: DataSource) : DelegatingDataSource(delegate) {
    override fun getConnection(): Connection = init(super.getConnection())

    override fun getConnection(username: String, password: String): Connection =
        init(super.getConnection(username, password))

    private fun init(c: Connection): Connection {
        try {
            c.createStatement().use { st ->
                st.execute("RESET app.user_id")
                st.execute("RESET app.context")
            }
            RlsContext.user()?.let { setVar(c, "app.user_id", it) }
            RlsContext.context()?.let { setVar(c, "app.context", it) }
        } catch (ex: Exception) {
            c.close()
            throw ex
        }
        return c
    }

    private fun setVar(c: Connection, name: String, value: String) {
        c.prepareStatement("SELECT set_config(?, ?, false)").use { ps ->
            ps.setString(1, name)
            ps.setString(2, value)
            ps.execute()
        }
    }
}

@Configuration
class RlsDataSourceConfig(private val props: DataSourceProperties) {
    @Bean
    @Primary
    fun rlsDataSource(): DataSource {
        val hikari = props.initializeDataSourceBuilder().type(HikariDataSource::class.java).build()
        return RlsAwareDataSource(hikari)
    }
}
