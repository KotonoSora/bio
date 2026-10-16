package com.resume.publish.config

import com.zaxxer.hikari.HikariDataSource
import com.resume.publish.security.RlsUserHolder
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DelegatingDataSource
import java.sql.Connection
import javax.sql.DataSource

/**
 * Every borrowed connection is RESET then SET to the server-set request user, so
 * RLS policies (`app.user_id`) hold on pooled connections. Unset user = deny.
 * Pooled state can never leak across requests: reset runs before every set.
 */
class RlsAwareDataSource(delegate: DataSource) : DelegatingDataSource(delegate) {
    override fun getConnection(): Connection = init(super.getConnection())

    override fun getConnection(username: String, password: String): Connection =
        init(super.getConnection(username, password))

    private fun init(c: Connection): Connection {
        try {
            c.createStatement().use { st ->
                st.execute("RESET app.user_id")
                RlsUserHolder.get()?.let { u ->
                    st.execute("SELECT set_config('app.user_id', '${u.replace("'", "''")}', false)")
                }
            }
        } catch (ex: Exception) {
            c.close()
            throw ex
        }
        return c
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
