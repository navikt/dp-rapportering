package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningsendring.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import java.util.UUID

class GodkjennPeriodeHendelse(
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

    constructor(ident: String, rapporteringId: UUID, kilde: Saksbehandler, begrunnelse: String) : this(
        UUID.randomUUID(),
        ident,
        rapporteringId,
        Godkjenningsendring(kilde, begrunnelse),
    )
}
