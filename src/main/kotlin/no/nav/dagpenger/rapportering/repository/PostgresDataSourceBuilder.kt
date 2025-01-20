package no.nav.dagpenger.rapportering.repository

import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output.CleanResult
import java.lang.System.getProperty
import java.lang.System.getenv
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ThreadLocalRandom

internal object PostgresDataSourceBuilder {
    const val DB_URL_KEY = "DB_JDBC_URL"

    private fun getOrThrow(key: String): String = getenv(key) ?: getProperty(key)

    val dataSource by lazy {
        HikariDataSource().apply {
            jdbcUrl = getOrThrow(DB_URL_KEY) + "&tcpKeepAlive=true"
            maximumPoolSize = 10
            minimumIdle = 1
            idleTimeout = 10000 // 10s
            connectionTimeout = 5000 // 5s
            maxLifetime = 30000 // 30s
            initializationFailTimeout = 5000 // 5s
        }
    }
    private val flyWayBuilder: FluentConfiguration =
        Flyway.configure().connectRetries(5)

    fun clean(): CleanResult =
        flyWayBuilder
            .cleanDisabled(false)
            .dataSource(dataSource)
            .load()
            .clean()

    internal fun runMigration() =
        flyWayBuilder
            .dataSource(dataSource)
            .load()
            .migrate()
            .migrations
            .size

    fun preparePartitions() {
        val logger = KotlinLogging.logger {}

        // Opprett nye og slett gamle partisjoner
        val timer = Timer()
        val timerTask: TimerTask =
            object : TimerTask() {
                override fun run() {
                    try {
                        dataSource.connection.prepareCall("SELECT manage_partitions()").use { pc ->
                            pc.execute()
                        }
                    } catch (e: Exception) {
                        logger.warn("Feil med partisjoner", e)
                    }
                }
            }

        // Hver pod får sin egen Timer og det kan skje at TimerTask'er jobber helt samtidig og vi får feil.
        // For å redusere sansynlighet for dette og øke pålitelighet kan vi starte Timer i random minute
        val randomMinute = ThreadLocalRandom.current().nextInt(0, 60)

        // Timer.schedule( ) kjøres med en gang hvis den planlagte første gangen er i fortiden
        // Da må vi sjekke om vi er før eller etter dette tidspunktet
        // Hvis før: som normalt, skal kjøres i dag
        // Hvis etter: skal kjøres i morgen
        val hour = 5
        val firstExecution = Calendar.getInstance()
        if (firstExecution[Calendar.HOUR_OF_DAY] > hour) {
            firstExecution.add(Calendar.DAY_OF_MONTH, 1)
        }
        firstExecution[Calendar.HOUR_OF_DAY] = hour
        firstExecution[Calendar.MINUTE] = randomMinute
        firstExecution[Calendar.SECOND] = 0

        timer.schedule(
            timerTask,
            firstExecution.time,
            (24 * 60 * 60 * 1000).toLong(),
        ) // %hour%:00:00 og videre hver 24 timer
    }
}
