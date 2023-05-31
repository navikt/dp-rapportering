package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

class SøknadInnsendtHendelse(meldingsreferanseId: UUID, ident: String) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    internal val fom: LocalDate

    // TODO: Skal etterhvert være basert på søknadsdato eller ønsker dagpenger fra dato
    init {
        val dagensDato = LocalDate.now()
        fom = dagensDato
    }
}
