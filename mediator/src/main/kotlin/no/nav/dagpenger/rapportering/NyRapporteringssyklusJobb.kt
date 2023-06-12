package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import java.time.LocalDate
import java.util.Calendar
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

internal object NyRapporteringssyklusJobb {
    private val logger = KotlinLogging.logger {}
    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast opprettelse av nye rapporteringssyklus",
            daemon = true,
            initialDelay = randomInitialDelay(),
            period = 15.minutes.inWholeMilliseconds,
            action = {
                try {
                    logger.info { "Starter opprettelse av ny rapporteringssyklus" }
                    mediator.nyRapporteringssyklus()
                } catch (e: Exception) {
                    logger.error(e) { "Opprettelse av ny rapporteringssyklus feilet" }
                }
            },
        )
    }

    private fun Mediator.nyRapporteringssyklus() {
        val identer: List<String> = hentIdenterMedRapporteringsplikt()

        identer.forEach { ident ->
            behandle(NyRapporteringssyklusHendelse(UUID.randomUUID(), ident, LocalDate.now()))
        }
    }

    private fun randomInitialDelay() = Random.nextInt(10).minutes.inWholeMilliseconds
}

fun main() {
    val timer = Timer()
    // Get current date and time
    val currentTime = Calendar.getInstance()
    // Set the time to Monday at 15:00
    currentTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    currentTime.set(Calendar.HOUR_OF_DAY, 15)
    currentTime.set(Calendar.MINUTE, 0)
    currentTime.set(Calendar.SECOND, 0)
    // Calculate the delay until the next Monday at 15:00
    val delay = currentTime.timeInMillis - System.currentTimeMillis()
    // Schedule the task to run every week on Monday at 15:00
    timer.scheduleAtFixedRate(
        /* task = */
        object : TimerTask() {
            override fun run() {
                // Call your Kotlin function here
                yourFunction()
            }
        },
        /* delay = */
        delay,
        /* period = */
        7.days.inWholeMilliseconds,
    ) // Run every 7 days
    // Keep the main thread running to allow the scheduled task to execute
    Thread.currentThread().join()
}

fun yourFunction() {
    // Your function code here
    println("Executing yourFunction() at ${Calendar.getInstance().time}")
}
