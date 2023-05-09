package no.nav.dagpenger.aktivitetslogg

import no.nav.dagpenger.aktivitetslogg.Aktivitet.Behov

// Understands issues that arose when analyzing a JSON message
// Implements Collecting Parameter in Refactoring by Martin Fowler
// Implements Visitor pattern to traverse the messages
class Aktivitetslogg(
    private var forelder: Aktivitetslogg? = null,
) : IAktivitetslogg {
    private val aktiviteter = mutableListOf<Aktivitet>()
    private val kontekster = mutableListOf<Aktivitetskontekst>()
    private val observers = mutableListOf<AktivitetsloggObserver>()

    fun accept(visitor: AktivitetsloggVisitor) {
        visitor.preVisitAktivitetslogg(this)
        aktiviteter.forEach { it.accept(visitor) }
        visitor.postVisitAktivitetslogg(this)
    }

    override fun registrer(observer: AktivitetsloggObserver) {
        observers.add(observer)
    }

    override fun info(melding: String, vararg params: Any?) {
        val formatertMelding = if (params.isEmpty()) melding else String.format(melding, *params)
        add(Aktivitet.Info.opprett(kontekster.toSpesifikk(), formatertMelding))
    }

    override fun behov(type: Behov.Behovtype, melding: String, detaljer: Map<String, Any?>) {
        add(Behov.opprett(type, kontekster.toSpesifikk(), melding, detaljer))
    }

    private fun add(aktivitet: Aktivitet) {
        observers.forEach { aktivitet.notify(it) }
        this.aktiviteter.add(aktivitet)
        forelder?.add(aktivitet)
    }

    private fun MutableList<Aktivitetskontekst>.toSpesifikk() = this.map { it.toSpesifikkKontekst() }

    override fun harAktiviteter() = info().isNotEmpty() || behov().isNotEmpty()

    override fun barn() = Aktivitetslogg(this).also { it.kontekster.addAll(this.kontekster) }

    override fun toString() = this.aktiviteter.map { it.inOrder() }.joinToString(separator = "\n") { it }

    override fun aktivitetsteller() = aktiviteter.size

    override fun kontekst(kontekst: Aktivitetskontekst) {
        val spesifikkKontekst = kontekst.toSpesifikkKontekst()
        val index = kontekster.indexOfFirst { spesifikkKontekst.sammeType(it) }
        if (index >= 0) fjernKonteksterFraOgMed(index)
        kontekster.add(kontekst)
    }

    override fun kontekst(kontekst: Subaktivitetskontekst) {
        forelder = kontekst.aktivitetslogg
        kontekst(kontekst as Aktivitetskontekst)
    }

    private fun fjernKonteksterFraOgMed(indeks: Int) {
        val antall = kontekster.size - indeks
        repeat(antall) { kontekster.removeLast() }
    }

    override fun toMap(mapper: AktivitetsloggMappingPort): Map<String, List<Map<String, Any>>> = mapper.map(this)

    fun logg(vararg kontekst: Aktivitetskontekst): Aktivitetslogg {
        return Aktivitetslogg(this).also {
            it.aktiviteter.addAll(
                this.aktiviteter.filter { aktivitet ->
                    kontekst.any { it in aktivitet }
                },
            )
        }
    }

    internal fun logg(vararg kontekst: String): Aktivitetslogg {
        return Aktivitetslogg(this).also { aktivitetslogg ->
            aktivitetslogg.aktiviteter.addAll(
                this.aktiviteter.filter { aktivitet ->
                    kontekst.any { kontekst -> kontekst in aktivitet.kontekster.map { it.kontekstType } }
                },
            )
        }
    }

    override fun kontekster() =
        aktiviteter
            .groupBy { it.kontekst(null) }
            .map { Aktivitetslogg(this).apply { aktiviteter.addAll(it.value) } }

    private fun info() = Aktivitet.Info.filter(aktiviteter)
    override fun behov() = Behov.filter(aktiviteter)

    class AktivitetException internal constructor(private val aktivitetslogg: Aktivitetslogg) :
        RuntimeException(aktivitetslogg.toString()) {
        fun kontekst() = aktivitetslogg.kontekster.fold(mutableMapOf<String, String>()) { result, kontekst ->
            result.apply { putAll(kontekst.toSpesifikkKontekst().kontekstMap) }
        }

        fun aktivitetslogg() = aktivitetslogg
    }
}

interface AktivitetsloggMappingPort {
    fun map(log: Aktivitetslogg): Map<String, List<Map<String, Any>>>
}
