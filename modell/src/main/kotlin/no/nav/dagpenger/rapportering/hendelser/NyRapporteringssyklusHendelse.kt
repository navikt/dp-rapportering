package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.FastsettBeregningsdatoStrategi
import java.time.LocalDate
import java.util.UUID

class NyRapporteringssyklusHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val fom: LocalDate,
    val beregningsdatoStrategi: FastsettBeregningsdatoStrategi,
) : PersonHendelse(
        meldingsreferanseId,
        ident,
        Aktivitetslogg(),
    )
