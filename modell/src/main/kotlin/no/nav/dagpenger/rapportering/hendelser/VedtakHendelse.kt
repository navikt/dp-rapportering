package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

sealed class VedtakHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val utfall: Utfall,
    internal val virkningsdato: LocalDate,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
)

class VedtakInnvilgetHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    virkningsdato: LocalDate,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Innvilget, virkningsdato)

class VedtakAvslåttHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    virkningsdato: LocalDate,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Avslått, virkningsdato)

enum class Utfall {
    Innvilget,
    Avslått,
}
