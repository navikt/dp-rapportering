package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.FastsettBeregningsdatoStrategi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringspliktDatoHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val opprettet: LocalDateTime,
    internal val søknadInnsendtDato: LocalDate,
    internal val beregningsdatoStrategi: FastsettBeregningsdatoStrategi,
) : PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    )
