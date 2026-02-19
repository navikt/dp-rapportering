package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.repository.BekreftelsesmeldingRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.measureTime

internal class SendBekreftelsesmeldingerJob(
    meterRegistry: MeterRegistry,
    private val httpClient: HttpClient,
    private val bekreftelsesmeldingRepository: BekreftelsesmeldingRepository,
    private val rapporteringRepository: RapporteringRepository,
    private val arbeidssøkerService: ArbeidssøkerService,
) : Task {
    private val logger = KotlinLogging.logger { }
    private val metrikker: JobbkjoringMetrikker = JobbkjoringMetrikker(meterRegistry, this::class.simpleName!!)

    override fun execute() {
        try {
            if (!isLeader(httpClient, logger)) {
                logger.info { "Pod er ikke leader, så jobb for å sende bekreftelsesmeldinger startes ikke" }
                return
            }

            logger.info { "Starter jobb for å sende bekreftelsesmeldinger" }

            var rowsAffected: Int
            val tidBrukt =
                measureTime {
                    runBlocking {
                        val lagretData = bekreftelsesmeldingRepository.hentBekreftelsesmeldingerSomSkalSendes(LocalDate.now())
                        rowsAffected = lagretData.size

                        lagretData.forEach { (id, rapporteringId, ident) ->
                            try {
                                val rapporteringsperiode = rapporteringRepository.hentRapporteringsperiode(rapporteringId, ident)
                                if (rapporteringsperiode == null) {
                                    logger.error { "Fant ikke rapporteringsperiode med id $rapporteringId" }
                                    return@forEach
                                }

                                val sendtBekreftelseId = arbeidssøkerService.sendBekreftelse(ident, rapporteringsperiode)

                                if (sendtBekreftelseId != null) {
                                    bekreftelsesmeldingRepository.oppdaterBekreftelsesmelding(
                                        id,
                                        sendtBekreftelseId,
                                        LocalDateTime.now(),
                                    )
                                }
                            } catch (e: Exception) {
                                logger.warn(e) {
                                    "Klarte ikke å sende bekreftelsesmelding med id $id og rapporteringId $rapporteringId"
                                }
                            }
                        }
                    }
                }

            logger.info {
                "Jobb for å sende bekreftelsesmeldinger ferdig. Brukte ${tidBrukt.inWholeSeconds} sekund(er)."
            }
            metrikker.jobbFullfort(tidBrukt, rowsAffected)
        } catch (e: Exception) {
            logger.warn(e) { "Jobb for å sende bekreftelsesmeldinger feilet" }
            metrikker.jobbFeilet()
        }
    }
}
