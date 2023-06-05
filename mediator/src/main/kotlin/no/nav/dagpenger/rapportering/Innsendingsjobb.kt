package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal object Innsendingsjobb {
    private val logger = KotlinLogging.logger {}
    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast innsending av godkjente rapporteringsperioder",
            daemon = true,
            initialDelay = randomInitialDelay(),
            period = 15.minutes.inWholeMilliseconds,
            action = {
                try {
                    mediator.rapporteringsfrist()
                } catch (e: Exception) {
                    logger.error(e) { "Innsending av godkjente rapporteringsperioder feilet" }
                }
            },
        )
    }

    private fun Mediator.rapporteringsfrist() {
        val identer: List<String> = hentIdenterMedGodkjentPeriode()

        identer.forEach { ident ->
            behandle(RapporteringsfristHendelse(UUID.randomUUID(), ident, LocalDate.now()))
        }
    }

    private fun randomInitialDelay() = Random.nextInt(10).minutes.inWholeMilliseconds
}
