package no.nav.dagpenger.rapportering.repository

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

internal object Postgres {
    private val database by lazy {
        PostgreSQLContainer("postgres:15").apply {
            start()
        }
    }
    val dataSource: DataSource =
        HikariDataSource().apply {
            jdbcUrl = database.jdbcUrl
            username = database.username
            password = database.password
        }

    private val flyWayBuilder = Flyway.configure().connectRetries(5)

    fun withMigratedDb(block: () -> Unit) {
        withCleanDb {
            runMigration()
            block()
        }
    }

    private fun withCleanDb(block: () -> Unit) {
        flyWayBuilder.cleanDisabled(false).dataSource(dataSource).load().clean()
        block()
    }

    private fun runMigration() {
        flyWayBuilder.also { flyWayBuilder ->
            flyWayBuilder
                .dataSource(dataSource)
                .load()
                .migrate()
                .migrations
                .size
        }
    }
}
