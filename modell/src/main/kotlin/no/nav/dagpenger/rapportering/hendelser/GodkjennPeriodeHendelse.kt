package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.Godkjenning
import no.nav.dagpenger.rapportering.Godkjenning.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenning.Sluttbruker
import java.util.UUID

class GodkjennPeriodeHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringsperiodeId: UUID,
    val godkjenning: Godkjenning,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    constructor(ident: String, rapporteringId: UUID) : this(
        UUID.randomUUID(),
        ident,
        rapporteringId,
        Godkjenning(Sluttbruker(ident)),
    )

    constructor(ident: String, rapporteringId: UUID, kilde: Saksbehandler, begrunnelse: String) : this(
        UUID.randomUUID(),
        ident,
        rapporteringId,
        Godkjenning(kilde, begrunnelse),
    )
}
