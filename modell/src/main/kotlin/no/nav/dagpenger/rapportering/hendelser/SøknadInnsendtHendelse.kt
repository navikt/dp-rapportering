package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class SøknadInnsendtHendelse(meldingsreferanseId: UUID, ident: String) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    // TODO: Bruke søknadsdato i stedet for dagens dato
    fun fraOgMed(): LocalDate {
        val dagensDato = LocalDate.now()
        return dagensDato.finnFørsteMandagIUken()
    }
}

fun LocalDate.finnFørsteMandagIUken() = this.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
