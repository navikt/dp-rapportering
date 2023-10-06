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
    internal val sakId: UUID,
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
    sakId: UUID,
    val beregningsdatoStrategi: FastsettBeregningsdatoStrategi,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Innvilget, virkningsdato, opprettet, sakId)

class VedtakAvslåttHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    virkningsdato: LocalDate,
    opprettet: LocalDateTime,
    sakId: UUID,
) : VedtakHendelse(meldingsreferanseId, ident, Utfall.Avslått, virkningsdato, opprettet, sakId)

enum class Utfall {
    Innvilget,
    Avslått,
}
