package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
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
