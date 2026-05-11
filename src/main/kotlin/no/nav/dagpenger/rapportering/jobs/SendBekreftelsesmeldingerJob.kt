package no.nav.dagpenger.rapportering.jobs

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.repository.BekreftelsesmeldingRepository
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

internal class SendBekreftelsesmeldingerJob(
    private val httpClient: HttpClient,
    private val bekreftelsesmeldingRepository: BekreftelsesmeldingRepository,
    private val rapporteringRepository: RapporteringRepository,
    private val arbeidssøkerService: ArbeidssøkerService,
    private val jobbkjøringMetrikker: JobbkjøringMetrikker,
) {
    private val logger = KotlinLogging.logger { }

    internal fun start(
        initialDelayMinutes: Long = 5,
        periodeMinutes: Long = 30,
    ) {
        fixedRateTimer(
            name = "Jobb for å sende bekreftelsesmeldinger",
            daemon = true,
            initialDelay = initialDelayMinutes.minutes.inWholeMilliseconds,
            period = periodeMinutes.minutes.inWholeMilliseconds,
            action = {
                execute()
            },
        )
    }

    private fun execute() {
        try {
            if (!isLeader(httpClient, logger)) {
                logger.info { "Pod er ikke leader, så jobb for å sende bekreftelsesmeldinger kjøres ikke" }
                return
            }

            logger.info { "Kjører jobb for å sende bekreftelsesmeldinger" }

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
                                } else {
                                    logger.warn {
                                        "sendBekreftelse returnerte null for bekreftelsesmeldingId=$id, rapporteringId=$rapporteringId. " +
                                            "Meldingen vil bli forsøkt sendt på nytt så lenge denne tilstanden vedvarer."
                                    }
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
            jobbkjøringMetrikker.jobbFullfort(tidBrukt, rowsAffected)
        } catch (e: Exception) {
            logger.warn(e) { "Jobb for å sende bekreftelsesmeldinger feilet" }
            jobbkjøringMetrikker.jobbFeilet()
        } finally {
            jobbkjøringMetrikker.jobbSjekketOmDenSkulleKjøre()
        }
    }
}
