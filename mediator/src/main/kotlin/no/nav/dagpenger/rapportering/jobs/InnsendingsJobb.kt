package no.nav.dagpenger.rapportering.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal object InnsendingsJobb {
    private val logger = KotlinLogging.logger {}
    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast innsending av godkjente rapporteringsperioder hvor beregningsdatoen har passert",
            daemon = true,
            initialDelay = randomInitialDelay(),
            period = 15.minutes.inWholeMilliseconds,
            action = {
                try {
                    mediator.beregningsdatoPassert()
                } catch (e: Exception) {
                    logger.error(e) { "Innsending av godkjente rapporteringsperioder feilet" }
                }
            },
        )
    }

    private fun Mediator.beregningsdatoPassert() {
        val identer: List<String> = hentIdenterMedGodkjentPeriode()
        logger.info { "Fant ${identer.size} personer med godkjente perioder, starter innsendingsjobb for disse" }

        identer.forEach { ident ->
            behandle(BeregningsdatoPassertHendelse(UUID.randomUUID(), ident, LocalDate.now()))
        }

        val oppdaterteIdenter = hentIdenterMedGodkjentPeriode()
        logger.info { "Sendte inn ${identer.size - oppdaterteIdenter.size} perioder" }
    }

    private fun randomInitialDelay() = Random.nextInt(10).minutes.inWholeMilliseconds
}
