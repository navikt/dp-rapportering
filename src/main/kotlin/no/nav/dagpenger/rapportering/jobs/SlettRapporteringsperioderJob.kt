package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.service.RapporteringService
import kotlin.time.measureTime

internal class SlettRapporteringsperioderJob(
    meterRegistry: MeterRegistry,
    private val httpClient: HttpClient,
    private val rapporteringService: RapporteringService,
) : Task {
    private val logger = KotlinLogging.logger { }
    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

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
            metrikker.jobbFullfort(tidBrukt, rowsAffected)
        } catch (e: Exception) {
            logger.warn(e) { "Slettejobb feilet" }
            metrikker.jobbFeilet()
        }
    }
}
