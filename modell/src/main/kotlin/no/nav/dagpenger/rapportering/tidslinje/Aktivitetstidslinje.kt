package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import java.time.LocalDate

internal class Aktivitetstidslinje(dager: List<Aktivitet> = emptyList()) {
    private val aktiviteter = dager.toMutableList()

    // TODO: Kun brukt i tester - må fjernes
    internal fun håndter(aktivitet: Aktivitet) {
        aktiviteter.add(aktivitet)
    }

    fun håndter(hendelse: NyAktivitetHendelse) {
        aktiviteter.addAll(hendelse.aktiviteter)
    }

    fun antallAktiviteter() = aktiviteter.size
    fun antallDager() = Aktivitet.perDag(aktiviteter).size

    fun forPeriode(periode: ClosedRange<LocalDate>) = Aktivitetstidslinje(aktiviteter.filter { it.dekkesAv(periode) })

    fun håndter(hendelse: NyRapporteringHendelse) {
        aktiviteter.forEach { it.håndter(hendelse) }
    }
}
