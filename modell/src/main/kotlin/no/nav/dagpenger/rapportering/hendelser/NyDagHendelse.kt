package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.IAktivitetslogg
import no.nav.dagpenger.rapportering.Dag
import java.time.LocalDate
import java.util.UUID

class NyDagHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    aktivitetslogg: IAktivitetslogg,
    private val dato: LocalDate,
    private val fravær: Number,
    private val timer: Number,
) :
    PersonHendelse(meldingsreferanseId, ident, aktivitetslogg) {
    internal fun dag() = Dag(dato, fravær, timer)
}
