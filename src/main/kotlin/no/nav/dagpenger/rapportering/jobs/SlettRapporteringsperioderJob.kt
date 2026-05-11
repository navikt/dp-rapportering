package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.service.RapporteringService
import kotlin.time.measureTime

internal class SlettRapporteringsperioderJob(
    private val httpClient: HttpClient,
    private val rapporteringService: RapporteringService,
    private val jobbkjøringMetrikker: JobbkjøringMetrikker,
) : Task {
    private val logger = KotlinLogging.logger { }

    override fun execute() {
        try {
            if (!isLeader(httpClient, logger)) {
                logger.info { "Pod er ikke leader, så jobb for å slette mellomlagrede rapporteringsperioder startes ikke" }
                return
            }

            logger.info { "Starter jobb for å slette mellomlagrede rapporteringsperioder" }

            var rowsAffected: Int
            val tidBrukt =
                measureTime {
                    rowsAffected = runBlocking { rapporteringService.slettMellomlagredeRapporteringsperioder() }
                }

            logger.info {
                "Jobb for å slette mellomlagrede rapporteringsperioder ferdig. Brukte ${tidBrukt.inWholeSeconds} sekund(er)."
            }
            jobbkjøringMetrikker.jobbFullfort(tidBrukt, rowsAffected)
        } catch (e: Exception) {
            logger.warn(e) { "Slettejobb feilet" }
            jobbkjøringMetrikker.jobbFeilet()
        } finally {
            jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
        }
    }
}
