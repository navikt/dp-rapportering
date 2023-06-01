package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetstidslinjeVisitor
import no.nav.dagpenger.rapportering.Dag
import java.time.LocalDate

data class Aktivitetstidslinje internal constructor(
    private val dager: MutableSet<Dag> = mutableSetOf(),
) : MutableCollection<Dag> by dager {
    constructor(fraOgMed: LocalDate, tilOgMed: LocalDate) : this(fraOgMed..tilOgMed)
    constructor(periode: ClosedRange<LocalDate>) : this(
        mutableSetOf<Dag>().apply {
            val datesUntil = periode.start.datesUntil(periode.endInclusive.plusDays(1))
            datesUntil.forEach {
                add(Dag(it))
            }
        },
    )

    val dagerMedAktivitet get() = dager.count { it.harAktivitet() }

    fun leggTilFritak(vararg dato: LocalDate) {
        dato.forEach { fritak ->
            this.single { it.sammenfallerMed(fritak) }.leggTilFritak()
        }
    }

    fun leggTilAktivitet(aktivitet: Aktivitet) {
        this.single { it.sammenfallerMed(aktivitet.dato) }.leggTilAktivitet(aktivitet)
    }

    fun accept(visitor: AktivitetstidslinjeVisitor) {
        visitor.preVisit(this)
        dager.forEach { it.accept(visitor) }
        visitor.postVisit(this)
    }
}
