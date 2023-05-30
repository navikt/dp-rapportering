package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.AktivitetstidslinjeVisitor
import java.time.LocalDate

interface Tidslinje : Collection<Aktivitet> {
    val dagerMedAktivitet: Int

    fun accept(visitor: AktivitetstidslinjeVisitor)
}

internal data class Aktivitetstidslinje internal constructor(
    private val aktiviteter: MutableSet<Aktivitet> = mutableSetOf(),
) : MutableCollection<Aktivitet> by aktiviteter, Tidslinje {
    override val dagerMedAktivitet get() = Aktivitet.perDag(aktiviteter).size

    fun forPeriode(periode: ClosedRange<LocalDate>) = Aktivitetsperiode(periode.start, periode.endInclusive)

    fun forPeriode(fraOgMed: LocalDate, tilOgMed: LocalDate) = Aktivitetsperiode(fraOgMed, tilOgMed)

    override fun accept(visitor: AktivitetstidslinjeVisitor) {
        visitor.visit(aktiviteter.toList())
    }

    internal inner class Aktivitetsperiode(fraOgMed: LocalDate, tilOgMed: LocalDate) : Tidslinje {
        internal val periode = fraOgMed..tilOgMed
        private val aktiviteter
            get() = this@Aktivitetstidslinje.aktiviteter.filter {
                it.dekkesAv(periode)
            }

        override fun accept(visitor: AktivitetstidslinjeVisitor) {
            visitor.visit(aktiviteter.toList())
        }

        override val dagerMedAktivitet get() = Aktivitet.perDag(aktiviteter).size

        override fun iterator() = aktiviteter.iterator()

        override val size get() = aktiviteter.size

        override fun contains(element: Aktivitet) = aktiviteter.contains(element)

        override fun containsAll(elements: Collection<Aktivitet>) = aktiviteter.containsAll(elements)

        override fun isEmpty() = aktiviteter.isEmpty()
    }
}
