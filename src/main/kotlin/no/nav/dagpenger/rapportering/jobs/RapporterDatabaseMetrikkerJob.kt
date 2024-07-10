package no.nav.dagpenger.rapportering.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal object RapporterDatabaseMetrikkerJob {
    private val logger = KotlinLogging.logger {}
    private val metrics = DatabaseMetrikker

    internal fun start(
        rapporteringRepository: RapporteringRepository,
        journalfoeringRepository: JournalfoeringRepository,
    ) {
        fixedRateTimer(
            name = "Fast rapportering av lagrede elementer i databasen",
            daemon = true,
            initialDelay = Random.nextInt(5).minutes.inWholeMilliseconds,
            period = 10.minutes.inWholeMilliseconds,
            action = {
                try {
                    metrics.lagredeRapporteringsperioder.set(rapporteringRepository.hentAntallRapporteringsperioder().toDouble())
                    metrics.lagredeJournalposter.set(journalfoeringRepository.hentAntallJournalposter().toDouble())
                    metrics.midlertidigLagredeJournalposter.set(journalfoeringRepository.hentAntallMidlertidligeJournalposter().toDouble())
                } catch (e: Exception) {
                    logger.warn(e) { "Uthenting av metrikker for lagrede elementer i databasen feilet" }
                    metrics.lagredeRapporteringsperioder.set(-1.0)
                    metrics.lagredeJournalposter.set(-1.0)
                    metrics.midlertidigLagredeJournalposter.set(-1.0)
                }
            },
        )
    }
}
