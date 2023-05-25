package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import java.util.UUID

class NyRapporteringHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringId: UUID,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    internal var aktivitetstidslinje = Aktivitetstidslinje()
}
