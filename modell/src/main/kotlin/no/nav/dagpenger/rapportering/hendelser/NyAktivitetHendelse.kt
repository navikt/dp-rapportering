package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID

class NyAktivitetHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val aktiviteter: List<Aktivitet>,
) : PersonHendelse(meldingsreferanseId, ident, Aktivitetslogg()) {
    constructor(
        meldingsreferanseId: UUID,
        ident: String,
        vararg aktivitet: Aktivitet,
    ) : this(meldingsreferanseId, ident, aktivitet.toList())
}
