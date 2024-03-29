package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import java.time.LocalDate
import java.util.UUID

class BeregningsdatoPassertHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    internal val beregningsdato: LocalDate,
) :
    PersonHendelse(
            meldingsreferanseId,
            ident,
            Aktivitetslogg(),
        )
