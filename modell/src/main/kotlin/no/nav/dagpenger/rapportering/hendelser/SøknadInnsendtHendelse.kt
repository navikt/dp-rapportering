package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

class SøknadInnsendtHendelse(meldingsreferanseId: UUID, ident: String) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    fun fraOgMed(): LocalDate = LocalDate.now()
}
