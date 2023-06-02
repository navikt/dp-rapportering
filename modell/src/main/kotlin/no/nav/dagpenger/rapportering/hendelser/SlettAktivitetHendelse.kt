package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.util.UUID

class SlettAktivitetHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringsperiodeId: UUID,
    val aktivitetId: UUID,
) : PersonHendelse(meldingsreferanseId, ident, Aktivitetslogg()) {
    constructor(ident: String, rapporteringsperiodeId: UUID, aktivitetId: UUID) : this(
        UUID.randomUUID(),
        ident,
        rapporteringsperiodeId,
        aktivitetId,
    )
}
