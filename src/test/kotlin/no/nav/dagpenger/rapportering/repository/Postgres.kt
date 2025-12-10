package no.nav.dagpenger.rapportering.repository

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

internal object Postgres {
    val database by lazy {
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

    fun withMigratedDb(block: suspend () -> Unit) {
        withCleanDb {
            runMigration()
            runBlocking { block() }
        }
    }

    private fun withCleanDb(block: () -> Unit) {
        flyWayBuilder
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load()
            .clean()
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
