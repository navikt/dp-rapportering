package no.nav.dagpenger.rapportering.utils

import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus.TilUtfylling
import java.time.LocalDate
import java.time.temporal.WeekFields

object PeriodeUtils {
    fun finnPeriodeKode(fraOgMed: LocalDate): String =
        "${fraOgMed.get(WeekFields.ISO.weekBasedYear())}${fraOgMed.get(WeekFields.ISO.weekOfWeekBasedYear()).toString().padStart(2, '0')}"

    fun finnKanSendesFra(
        tilOgMed: LocalDate,
        justertInnsendingVerdi: Int?,
    ): LocalDate = tilOgMed.plusDays((justertInnsendingVerdi ?: -1).toLong())

    fun kanSendesInn(
        kanSendesFra: LocalDate,
        status: AdapterRapporteringsperiodeStatus,
    ): Boolean =
        if (status == TilUtfylling) {
            val naa = LocalDate.now()
            kanSendesFra.isBefore(naa) || kanSendesFra == naa
        } else {
            false
        }
}
