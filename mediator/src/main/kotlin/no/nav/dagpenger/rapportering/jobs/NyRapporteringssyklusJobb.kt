package no.nav.dagpenger.rapportering.jobs

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.strategiForBeregningsdato
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration.Companion.days

internal object NyRapporteringssyklusJobb {
    private val logger = KotlinLogging.logger {}
    private val UKEDAG_FOR_KJØRING = DayOfWeek.MONDAY
    private val TIDSPUNKT_FOR_KJØRING = LocalTime.of(15, 0)
    private val nå = ZonedDateTime.now()
    private val tidspunktForNesteKjøring = nå.with(TemporalAdjusters.nextOrSame(UKEDAG_FOR_KJØRING)).with(
        TIDSPUNKT_FOR_KJØRING,
    )
    private val millisekunderTilNesteKjøring = tidspunktForNesteKjøring.toInstant().toEpochMilli() - nå.toInstant()
        .toEpochMilli() // differansen i millisekunder mellom de to tidspunktene

    internal fun start(mediator: Mediator) {
        fixedRateTimer(
            name = "Fast opprettelse av ny rapporteringssyklus",
            daemon = true,
            initialDelay = millisekunderTilNesteKjøring.coerceAtLeast(0), // Har nåværende tidspunkt passert 'tidspunktForNesteKjøring' starter vi timeren umiddelbart
            period = 7.days.inWholeMilliseconds,
            action = {
                try {
                    logger.info { "Starter opprettelse av ny rapporteringssyklus" }
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
            behandle(
                NyRapporteringssyklusHendelse(
                    UUID.randomUUID(),
                    ident,
                    LocalDate.now(),
                    strategiForBeregningsdato,
                ),
            )
        }
    }
}
