package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.util.UUID

class GodkjennPeriodeHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringId: UUID,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    constructor(ident: String, rapporteringId: UUID) : this(UUID.randomUUID(), ident, rapporteringId)
}
