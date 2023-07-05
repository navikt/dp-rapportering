package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.Kalender
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType.Syk
import java.time.LocalDate

class Dag(
    internal val dato: LocalDate,
    private val aktiviteter: MutableList<Aktivitet>,
    private var harRapporteringslikt: Boolean = true,
) : Aktivitetskontekst {
    constructor(dato: LocalDate) : this(dato, mutableListOf())

    companion object {
        val eldsteDagFørst = Comparator<Dag> { a, b -> a.dato.compareTo(b.dato) }
    }

    private val muligeAktiviteter get() = if (aktiviteter.isEmpty()) listOf(Arbeid, Syk, Ferie) else emptyList()
    private val rapporteringspliktig get() = harRapporteringslikt || erHelligdag
    private val erHelligdag: Boolean
        get() = Kalender.erHelligdag(dato)

    internal fun sammenfallerMed(other: Dag) = this.dato == other.dato

    internal fun sammenfallerMed(other: LocalDate) = this.dato == other

    internal fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    fun leggTilAktivitet(aktivitet: Aktivitet): Boolean {
        if (!rapporteringspliktig) throw IllegalStateException("Kan ikke legge til aktivitet på dager uten rapporteringsplikt")
        // TODO: Logikk for om det er mulig å legge til aktivitet
        return aktiviteter.add(aktivitet)
    }

    fun gyldig() = true

    fun harAktivitet() = aktiviteter.isNotEmpty()
    fun leggTilFritak() {
        harRapporteringslikt = false
    }

    fun håndter(hendelse: GodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
    }

    fun håndter(hendelse: AvgodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
    }

    fun håndter(hendelse: SlettAktivitetHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
    }

    fun accept(visitor: DagVisitor) {
        visitor.visit(this, dato, aktiviteter, muligeAktiviteter)
        aktiviteter.forEach { it.accept(visitor) }
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("dag", mapOf("dato" to dato.toString()))
    }

    fun kopier() = Dag(dato, aktiviteter.kopier(), harRapporteringslikt)
}

private fun List<Aktivitet>.kopier() = this.map { it.kopier() }.toMutableList()
