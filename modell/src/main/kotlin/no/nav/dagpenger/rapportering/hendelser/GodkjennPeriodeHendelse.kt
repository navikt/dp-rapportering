package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningsendring.Saksbehandler
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import java.time.LocalDate
import java.util.UUID

class GodkjennPeriodeHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val rapporteringsperiodeId: UUID,
    val godkjenningsendring: Godkjenningsendring,
    val dato: LocalDate,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    constructor(ident: String, rapporteringId: UUID, dato: LocalDate = LocalDate.now()) : this(
        meldingsreferanseId = UUID.randomUUID(),
        ident = ident,
        rapporteringsperiodeId = rapporteringId,
        godkjenningsendring = Godkjenningsendring(Sluttbruker(ident)),
        dato = dato,
    )

    constructor(
        ident: String,
        rapporteringId: UUID,
        kilde: Saksbehandler,
        begrunnelse: String,
        dato: LocalDate = LocalDate.now(),
    ) : this(
        meldingsreferanseId = UUID.randomUUID(),
        ident = ident,
        rapporteringsperiodeId = rapporteringId,
        godkjenningsendring = Godkjenningsendring(kilde, begrunnelse),
        dato = dato,
    )
}
