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

        if (dagensDato.erMandag()) {
            return dagensDato
        } else {
            return finnForrigeMandag(fra = dagensDato)
        }
    }
}

fun LocalDate.erMandag(): Boolean = this.dayOfWeek == DayOfWeek.MONDAY
fun finnForrigeMandag(fra: LocalDate): LocalDate = fra.with(TemporalAdjusters.previous(DayOfWeek.MONDAY))
