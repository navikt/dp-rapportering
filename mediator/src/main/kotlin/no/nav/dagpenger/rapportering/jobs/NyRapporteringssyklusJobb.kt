package no.nav.dagpenger.rapportering.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.metrikker.JobbKjøringMetrikker
import no.nav.dagpenger.rapportering.strategiForBeregningsdato
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

internal object NyRapporteringssyklusJobb {
    private val logger = KotlinLogging.logger {}
    private val metrics = JobbKjøringMetrikker(this::class.java.simpleName)

    private val UKEDAG_FOR_KJØRING = DayOfWeek.MONDAY
    private val TIDSPUNKT_FOR_KJØRING = LocalTime.of(15, 0)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjøring =
        nå.with(TemporalAdjusters.nextOrSame(UKEDAG_FOR_KJØRING)).with(
            TIDSPUNKT_FOR_KJØRING,
        )
    private val millisekunderTilNesteKjøring =
        tidspunktForNesteKjøring.toInstant().toEpochMilli() -
            nå.toInstant()
                .toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    @OptIn(ExperimentalTime::class)
    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast opprettelse av ny rapporteringssyklus",
            daemon = true,
            // Har nåværende tidspunkt passert 'tidspunktForNesteKjøring' starter vi timeren umiddelbart
            initialDelay = millisekunderTilNesteKjøring.coerceAtLeast(0),
            period = 7.days.inWholeMilliseconds,
            action = {
                try {
                    logger.info { "Starter opprettelse av ny rapporteringssyklus" }
                    var antallIdenterMedRapporteringsplikt: Int
                    val tidBrukt =
                        measureTime {
                            antallIdenterMedRapporteringsplikt = mediator.nyRapporteringssyklus()
                        }
                    metrics.jobbFullført(tidBrukt, antallIdenterMedRapporteringsplikt)
                } catch (e: Exception) {
                    logger.error(e) { "Opprettelse av ny rapporteringssyklus feilet" }
                    metrics.jobbFeilet()
                }
            },
        )
    }

    private fun Mediator.nyRapporteringssyklus(): Int {
        val identer: List<String> = hentIdenterMedRapporteringsplikt()

        identer.forEach { ident ->
            behandle(
                NyRapporteringssyklusHendelse(
                    UUID.randomUUID(),
                    ident,
                    LocalDate.now(),
                    strategiForBeregningsdato,
                ),
            )
        }

        return identer.size
    }
}
