package no.nav.dagpenger.rapportering.repository

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.internal.configuration.ConfigUtils
import java.lang.System.getProperty
import java.lang.System.getenv

internal object PostgresDataSourceBuilder {
    const val DB_URL_KEY = "DB_JDBC_URL"

    private fun getOrThrow(key: String): String = getenv(key) ?: getProperty(key)

    val dataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl = getOrThrow(DB_URL_KEY)
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 10001
            connectionTimeout = 1000
            maxLifetime = 30001
            initializationFailTimeout = 5000
        }
    }
    private val flyWayBuilder: FluentConfiguration =
        Flyway.configure().connectRetries(5)

    fun clean() =
        flyWayBuilder
            .cleanDisabled(getOrThrow(ConfigUtils.CLEAN_DISABLED).toBooleanStrict())
            .dataSource(dataSource).load().clean()

    internal fun runMigration() =
        flyWayBuilder
            .dataSource(dataSource)
            .load()
            .migrate()
            .migrations
            .size
}
