package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.FastsettBeregningsdatoStrategi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

sealed class VedtakHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val utfall: Utfall,
    internal val virkningsdato: LocalDate,
    internal val opprettet: LocalDateTime,
) : PersonHendelse(
    meldingsreferanseId,
    ident,
    Aktivitetslogg(),
)

class VedtakInnvilgetHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    virkningsdato: LocalDate,
    opprettet: LocalDateTime,
    val beregningsdatoStrategi: FastsettBeregningsdatoStrategi,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Innvilget, virkningsdato, opprettet)

class VedtakAvslåttHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    virkningsdato: LocalDate,
    opprettet: LocalDateTime,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Avslått, virkningsdato, opprettet)

enum class Utfall {
    Innvilget,
    Avslått,
}
