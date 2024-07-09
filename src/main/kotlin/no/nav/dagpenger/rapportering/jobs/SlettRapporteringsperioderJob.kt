package no.nav.dagpenger.rapportering.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.metrics.JobbkjoringMetrikker
import no.nav.dagpenger.rapportering.service.RapporteringService
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
import kotlin.time.measureTime

internal object SlettRapporteringsperioderJob {
    private val logger = KotlinLogging.logger { }
    private val metrics = JobbkjoringMetrikker(this::class.java.simpleName)
    private val TIDSPUNKT_FOR_KJORING = LocalTime.of(0, 1)
    private val naa = ZonedDateTime.now()
    private val tidspunktForNesteKjoring = naa.with(TIDSPUNKT_FOR_KJORING).plusDays(1)
    private val millisekunderTilNesteKjoring =
        tidspunktForNesteKjoring.toInstant().toEpochMilli() -
            naa.toInstant().toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(rapporeringService: RapporteringService) {
        logger.info { "Tidspunkt for neste kjøring: $tidspunktForNesteKjoring" }
        fixedRateTimer(
            name = "Slett mellomlagrede rapporteringsperioder",
            daemon = true,
            // Har nåværende tidspunkt passert 'tidspunktForNesteKjoring' starter vi timeren umiddelbart
            initialDelay = millisekunderTilNesteKjoring.coerceAtLeast(0),
            period = 1.days.inWholeMilliseconds,
            action = {
                try {
                    var rowsAffected: Int
                    logger.info { "Starter jobb for å slette mellomlagrede rapporteringsperioder" }
                    val tidBrukt =
                        measureTime {
                            rowsAffected = rapporeringService.slettMellomlagredeRapporteringsperioder()
                        }
                    logger.info {
                        "Jobb for å slette mellomlagrede rapporteringsperioder ferdig. Brukte ${tidBrukt.inWholeSeconds} sekund(er)."
                    }
                    metrics.jobbFullfort(tidBrukt, rowsAffected)
                } catch (e: Exception) {
                    logger.warn(e) { "Slettejobb feilet" }
                    metrics.jobbFeilet()
                }
            },
        )
    }
}
