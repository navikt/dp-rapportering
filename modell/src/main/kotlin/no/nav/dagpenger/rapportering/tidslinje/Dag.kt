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
    strategiType: StrategiType? = null,
) : Aktivitetskontekst {
    private var strategi: MuligeAktiviteterStrategi = when (strategiType) {
        StrategiType.EnAktivitet -> EnAktivitetStrategi
        StrategiType.IngentingErMulig -> IngentingErMulig
        else -> EnAktivitetStrategi
    }

    constructor(dato: LocalDate) : this(dato, mutableListOf())

    companion object {
        val eldsteDagFørst = Comparator<Dag> { a, b -> a.dato.compareTo(b.dato) }
    }

    private val muligeAktiviteter get() = if (erHelligdag) emptyList() else strategi.mulige(aktiviteter)
    private val erHelligdag = Kalender.erHelligdag(dato)

    internal fun sammenfallerMed(other: Dag) = this.dato == other.dato

    internal fun sammenfallerMed(other: LocalDate) = this.dato == other

    internal fun dekkesAv(periode: ClosedRange<LocalDate>) = dato in periode

    fun leggTilAktivitet(aktivitet: Aktivitet): Boolean {
        if (aktivitet.type !in muligeAktiviteter) throw IllegalStateException("Ikke lov å legge til ${aktivitet.type}. Lovlige aktivtiteter: $muligeAktiviteter")

        return aktiviteter.add(aktivitet)
    }

    fun harAktivitet() = aktiviteter.isNotEmpty()

    fun leggTilFritak() {
        strategi = IngentingErMulig
    }

    fun håndter(hendelse: GodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
        strategi = IngentingErMulig
    }

    fun håndter(hendelse: AvgodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
        strategi = EnAktivitetStrategi
    }

    fun håndter(hendelse: SlettAktivitetHendelse) {
        hendelse.kontekst(this)
        aktiviteter.forEach { it.håndter(hendelse) }
    }

    fun accept(visitor: DagVisitor) {
        visitor.visit(this, dato, aktiviteter, muligeAktiviteter, strategi.type)
        aktiviteter.forEach { it.accept(visitor) }
    }

    override fun toSpesifikkKontekst(): SpesifikkKontekst {
        return SpesifikkKontekst("dag", mapOf("dato" to dato.toString()))
    }

    fun kopier() = Dag(dato, aktiviteter.kopier())

    enum class StrategiType {
        EnAktivitet,
        IngentingErMulig,
    }

    private interface MuligeAktiviteterStrategi {
        val type: StrategiType
        fun mulige(aktiviteter: List<Aktivitet>): List<Aktivitet.AktivitetType>
    }

    private object EnAktivitetStrategi : MuligeAktiviteterStrategi {
        override val type = StrategiType.EnAktivitet
        override fun mulige(aktiviteter: List<Aktivitet>) =
            if (aktiviteter.isEmpty()) listOf(Arbeid, Syk, Ferie) else emptyList()
    }

    private object IngentingErMulig : MuligeAktiviteterStrategi {
        override val type = StrategiType.IngentingErMulig

        override fun mulige(aktiviteter: List<Aktivitet>) = emptyList<Aktivitet.AktivitetType>()
    }
}

private fun List<Aktivitet>.kopier() = this.map { it.kopier() }.toMutableList()
