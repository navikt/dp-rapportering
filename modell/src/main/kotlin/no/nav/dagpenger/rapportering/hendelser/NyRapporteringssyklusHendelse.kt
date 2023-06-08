package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

class NyRapporteringssyklusHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val fom: LocalDate,
    val tom: LocalDate,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
)
