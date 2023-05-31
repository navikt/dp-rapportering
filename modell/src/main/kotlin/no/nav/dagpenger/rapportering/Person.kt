package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.aktivitetslogg.Subaktivitetskontekst
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje

class Person private constructor(
    val ident: String,
    internal val aktivitetstidslinje: Aktivitetstidslinje,
    private val rapporteringsperioder: MutableList<Rapporteringsperiode>,
    override val aktivitetslogg: Aktivitetslogg,
) : Subaktivitetskontekst, RapporteringsperiodeObserver {
    private val observers = mutableListOf<PersonObserver>()

    constructor(
        ident: String,
    ) : this(
        ident,
        Aktivitetstidslinje(),
        mutableListOf(),
        Aktivitetslogg(),
    )

    constructor(
        ident: String,
        rapporteringsperioder: MutableList<Rapporteringsperiode>,
    ) : this(
        ident,
        Aktivitetstidslinje(),
        rapporteringsperioder,
        Aktivitetslogg(),
    )

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler søknad innsendt")

        Rapporteringsperiode(
            this,
            rapporteringspliktFom = hendelse.fom,
        ).also {
            rapporteringsperioder.add(it)
            it.behandle(hendelse)
        }
    }

    fun behandle(hendelse: NyAktivitetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Tar imot ny aktivitet utført av bruker")

        aktivitetstidslinje.addAll(hendelse.aktiviteter)
    }

    fun behandle(hendelse: NyRapporteringsperiodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny rapporteringsperioder")

        Rapporteringsperiode(
            this,
            hendelse.fom,
        ).also {
            rapporteringsperioder.add(it)
            it.behandle(hendelse)
        }
    }

    fun behandle(hendelse: NyRapporteringHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny innrapportering")
        hendelse.aktivitetstidslinje = aktivitetstidslinje

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
        aktivitetstidslinje.accept(visitor)
        rapporteringsperioder.accept(visitor)
    }
}

private fun <E : Rapporteringsperiode> Collection<E>.accept(visitor: PersonVisitor) = forEach { it.accept(visitor) }
