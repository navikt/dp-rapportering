package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.util.UUID

/**
 * Hendelse som tvinger gjennom en innsending
 */
class ManuellInnsendingHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val rapporteringId: UUID,
) :
    PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    ) {
    constructor(ident: String, rapporteringsperiodeId: UUID) : this(
        UUID.randomUUID(),
        ident,
        rapporteringsperiodeId,
    )
}
