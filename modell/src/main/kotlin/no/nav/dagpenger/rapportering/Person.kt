package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.aktivitetslogg.Subaktivitetskontekst
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse

class Person private constructor(
    val ident: String,
    private val rapporteringsperioder: MutableList<Rapporteringsperiode>,
    override val aktivitetslogg: Aktivitetslogg,
) : Subaktivitetskontekst, RapporteringsperiodeObserver {
    private val observers = mutableListOf<PersonObserver>()

    constructor(
        ident: String,
    ) : this(
        ident,
        mutableListOf(),
        Aktivitetslogg(),
    )

    constructor(
        ident: String,
        rapporteringsperioder: MutableList<Rapporteringsperiode>,
    ) : this(
        ident,
        rapporteringsperioder,
        Aktivitetslogg(),
    )

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler søknad innsendt")

        Rapporteringsperiode(
            rapporteringspliktFom = hendelse.fom,
        ).also {
            rapporteringsperioder.add(it)
            it.registrer(this)
            it.behandle(hendelse)
        }
    }

    fun behandle(hendelse: NyRapporteringsperiodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny rapporteringsperioder")

        Rapporteringsperiode(
            hendelse.fom,
        ).also {
            rapporteringsperioder.add(it)
            it.registrer(this)
            it.behandle(hendelse)
        }
    }

    fun behandle(hendelse: NyRapporteringHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny innrapportering")

        rapporteringsperioder.single { it.rapporteringsperiodeId == hendelse.rapporteringId }
            .behandle(hendelse)
    }

    fun registrer(observer: PersonObserver) {
        observers.add(observer)
    }

    override fun rapporteringsperiodeEndret(event: RapporteringsperiodeObserver.RapporteringsperiodeEndret) {
        observers.forEach { it.rapporteringsperiodeEndret(event) }
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("person", mapOf("ident" to ident))

    override fun equals(other: Any?) = other is Person && this.ident == other.ident

    override fun hashCode() = this.ident.hashCode()
    fun accept(visitor: PersonVisitor) {
        visitor.visit(this, ident)
        rapporteringsperioder.accept(visitor)
    }
}

private fun <E : Rapporteringsperiode> Collection<E>.accept(visitor: PersonVisitor) = forEach { it.accept(visitor) }
