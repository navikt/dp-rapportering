package no.nav.dagpenger.rapportering.jobs

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.metrikker.JobbKjøringMetrikker
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal object InnsendingsJobb {
    private val logger = KotlinLogging.logger {}
    private val metrics = JobbKjøringMetrikker(this::class.java.simpleName)

    @OptIn(ExperimentalTime::class)
    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast innsending av godkjente rapporteringsperioder hvor beregningsdatoen har passert",
            daemon = true,
            initialDelay = randomInitialDelay(),
            period = 15.minutes.inWholeMilliseconds,
            action = {
                try {
                    var innsendtePerioder = 0
                    val tidBrukt = measureTime {
                        innsendtePerioder = mediator.beregningsdatoPassert()
                        runBlocking { delay(Random.nextLong(500, 25000)) }
                    }
                    metrics.jobbFullført(tidBrukt, innsendtePerioder)
                } catch (e: Exception) {
                    logger.error(e) { "Innsending av godkjente rapporteringsperioder feilet" }
                    metrics.jobbFeilet()
                }
            },
        )
    }

    private fun Mediator.beregningsdatoPassert(): Int {
        val identer: List<String> = hentIdenterMedGodkjentPeriode()
        logger.info { "Fant ${identer.size} personer med godkjente perioder, starter innsendingsjobb for disse" }

        identer.forEach { ident ->
            behandle(BeregningsdatoPassertHendelse(UUID.randomUUID(), ident, LocalDate.now()))
        }

        val oppdaterteIdenter = hentIdenterMedGodkjentPeriode()
        val innsendteIdenter = identer.size - oppdaterteIdenter.size
        logger.info { "Sendte inn $innsendteIdenter perioder" }

        return innsendteIdenter
    }

    private fun randomInitialDelay() = Random.nextInt(10).minutes.inWholeMilliseconds
}
