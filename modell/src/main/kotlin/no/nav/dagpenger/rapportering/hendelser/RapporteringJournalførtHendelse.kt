package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDateTime
import java.util.UUID

class RapporteringJournalførtHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val opprettet: LocalDateTime,
    internal val periodeId: String,
    internal val journalpostId: String,
) : PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    ) {
    constructor(
        ident: String,
        periodeId: String,
        journalpostId: String,
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) : this(
        meldingsreferanseId = UUID.randomUUID(),
        ident = ident,
        periodeId = periodeId,
        journalpostId = journalpostId,
        opprettet = opprettet,
    )
}
