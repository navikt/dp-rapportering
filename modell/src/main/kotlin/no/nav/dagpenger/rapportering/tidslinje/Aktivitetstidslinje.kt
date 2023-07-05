package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetstidslinjeVisitor
import java.time.LocalDate

data class Aktivitetstidslinje internal constructor(
    private val dager: MutableSet<Dag> = mutableSetOf(),
) : MutableCollection<Dag> by dager {
    constructor(periode: ClosedRange<LocalDate>, lagDag: (LocalDate) -> Dag = { Dag(it) }) : this(
        mutableSetOf<Dag>().apply {
            val datesUntil = periode.start.datesUntil(periode.endInclusive.plusDays(1))
            datesUntil.forEach {
                add(lagDag(it))
            }
        },
    )

    val dagerMedAktivitet get() = dager.count { it.harAktivitet() }

    fun leggTilFritak(vararg dato: LocalDate) {
        dato.forEach { fritak ->
            this.single { it.sammenfallerMed(fritak) }.leggTilFritak()
        }
    }

    fun leggTilAktivitet(aktivitet: Aktivitet) =
        this.single { dag -> dag.sammenfallerMed(aktivitet.dato) }.leggTilAktivitet(aktivitet)

    fun accept(visitor: AktivitetstidslinjeVisitor) {
        visitor.preVisit(this)
        dager.forEach { it.accept(visitor) }
        visitor.postVisit(this)
    }

    fun kopier() = Aktivitetstidslinje(
        dager.map {
            it.kopier()
        }.toMutableSet(),
    )
}
