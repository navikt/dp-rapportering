package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

class RapporteringsfristHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val rapporteringsfrist: LocalDate,
) :
    PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    )
