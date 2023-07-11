package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import java.util.UUID

class AvgodkjennPeriodeHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringsperiodeId: UUID,
    val godkjenningsendring: Godkjenningsendring,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    constructor(ident: String, rapporteringId: UUID) : this(
        UUID.randomUUID(),
        ident,
        rapporteringId,
        Godkjenningsendring(Sluttbruker(ident)),
    )

    constructor(ident: String, rapporteringId: UUID, kilde: Godkjenningsendring.Saksbehandler, begrunnelse: String) : this(
        UUID.randomUUID(),
        ident,
        rapporteringId,
        Godkjenningsendring(kilde, begrunnelse),
    )
}
