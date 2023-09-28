package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDateTime
import java.util.UUID

class RapporteringMidlertidigJournalførtHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val opprettet: LocalDateTime,
    internal val rapporteringsperiodeId: UUID,
    internal val journalpostId: String,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
) {
    constructor(
        ident: String,
        rapporteringsperiodeId: UUID,
        journalpostId: String,
        opprettet: LocalDateTime = LocalDateTime.now(),
    ) : this(
        meldingsreferanseId = UUID.randomUUID(),
        ident = ident,
        rapporteringsperiodeId = rapporteringsperiodeId,
        journalpostId = journalpostId,
        opprettet = opprettet,
    )
}
