package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.util.UUID

class VedtakHendelse(
    meldingsreferanseId: UUID,
    ident: String,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
)
