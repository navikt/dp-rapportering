package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

internal class RapporterDatabaseMetrikkerJob(
    private val databaseMetrikker: DatabaseMetrikker,
    private val jobbkjøringMetrikker: JobbkjøringMetrikker,
    private val initialDelay: Long = Random.nextInt(5).minutes.inWholeMilliseconds,
) {
    private val logger = KotlinLogging.logger {}

    internal fun start(
        rapporteringRepository: RapporteringRepository,
        journalfoeringRepository: JournalfoeringRepository,
    ) {
        fixedRateTimer(
            name = "Fast rapportering av lagrede elementer i databasen",
            daemon = true,
            initialDelay = initialDelay,
            period = 10.minutes.inWholeMilliseconds,
            action = {
                try {
                    val tidBrukt =
                        measureTime {
                            runBlocking {
                                databaseMetrikker.oppdater(
                                    lagredeRapporteringsperioder = rapporteringRepository.hentAntallRapporteringsperioder(),
                                    midlertidigLagredeJournalposter = journalfoeringRepository.hentAntallMidlertidigLagretData(),
                                )
                            }
                        }
                    jobbkjøringMetrikker.jobbFullfort(tidBrukt, 0)
                } catch (e: Exception) {
                    logger.warn(e) { "Uthenting av metrikker for lagrede elementer i databasen feilet" }
                    databaseMetrikker.oppdater(
                        lagredeRapporteringsperioder = -1,
                        midlertidigLagredeJournalposter = -1,
                    )
                    jobbkjøringMetrikker.jobbFeilet()
                } finally {
                    jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
                }
            },
        )
    }
}
