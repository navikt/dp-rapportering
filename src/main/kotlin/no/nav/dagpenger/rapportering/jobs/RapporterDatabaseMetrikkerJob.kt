package no.nav.dagpenger.rapportering.jobs

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

internal class RapporterDatabaseMetrikkerJob(
    private val metrikker: DatabaseMetrikker,
) {
    private val logger = KotlinLogging.logger {}

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
                    runBlocking {
                        metrikker.lagredeRapporteringsperioder?.set(
                            rapporteringRepository.hentAntallRapporteringsperioder(),
                        )
                        metrikker.lagredeJournalposter?.set(journalfoeringRepository.hentAntallJournalposter())
                        metrikker.midlertidigLagredeJournalposter?.set(
                            journalfoeringRepository.hentAntallMidlertidligeJournalposter(),
                        )
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Uthenting av metrikker for lagrede elementer i databasen feilet" }
                    metrikker.lagredeRapporteringsperioder?.set(-1)
                    metrikker.lagredeJournalposter?.set(-1)
                    metrikker.midlertidigLagredeJournalposter?.set(-1)
                }
            },
        )
    }
}
