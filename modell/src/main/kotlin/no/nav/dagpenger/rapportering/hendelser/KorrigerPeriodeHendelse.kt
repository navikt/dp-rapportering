package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.util.UUID

class KorrigerPeriodeHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringsperiodeId: UUID,
) : PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    ) {
    constructor(ident: String, rapporteringId: UUID) : this(UUID.randomUUID(), ident, rapporteringId)
}
