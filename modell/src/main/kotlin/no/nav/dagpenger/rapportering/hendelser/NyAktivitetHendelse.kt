package no.nav.dagpenger.rapportering.hendelser

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID

class NyAktivitetHendelse(
    meldingsreferanseId: UUID,
    ident: String,
    val aktivitet: Aktivitet,
) : PersonHendelse(meldingsreferanseId, ident, Aktivitetslogg())
