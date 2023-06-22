package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetslogg
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.aktivitetslogg.Subaktivitetskontekst
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.ManuellInnsendingHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import java.time.LocalDate

class Person private constructor(
    val ident: String,
    private val rapporteringsperioder: MutableList<Rapporteringsperiode>,
    private var rapporteringsplikt: TemporalCollection<Rapporteringsplikt>,
    override val aktivitetslogg: Aktivitetslogg,
) : Subaktivitetskontekst, RapporteringsperiodeObserver {
    private val observers = mutableListOf<PersonObserver>()

    constructor(
        ident: String,
    ) : this(
        ident,
        mutableListOf(),
        TemporalCollection<Rapporteringsplikt>().apply { put(LocalDate.now(), IngenRapporteringsplikt()) },
        Aktivitetslogg(),
    )

    constructor(
        ident: String,
        rapporteringsperioder: List<Rapporteringsperiode>,
        rapporteringsplikt: List<Rapporteringsplikt>,
    ) : this(
        ident,
        rapporteringsperioder.toMutableList(),
        rapporteringsplikt.fold(TemporalCollection<Rapporteringsplikt>()) { acc, rapporteringsplikt ->
            acc.also { it.put(rapporteringsplikt.rapporteringspliktFra, rapporteringsplikt) }
        },
        Aktivitetslogg(),
    )

    init {
        require(rapporteringsplikt.isNotEmpty()) { "Person må ha minst en rapporteringsplikt" }
        rapporteringsperioder.forEach { it.registrer(this) }
    }

    fun nyRapporteringsplikt(rapporteringsplikt: Rapporteringsplikt) {
        this.rapporteringsplikt.put(rapporteringsplikt.rapporteringspliktFra, rapporteringsplikt)
    }

    fun leggTilRapporteringsperiode(rapporteringsperiode: Rapporteringsperiode, hendelse: PersonHendelse) {
        hendelse.kontekst(this)

        if (rapporteringsperioder.any {
                it.gjelderFor(rapporteringsperiode.gjelderFra)
            }
        ) {
            hendelse.info("Det finnes allerede en rapporteringsperiode for denne perioden")
            return
        }

        hendelse.info("Legger til ny rapporteringsperiode")
        rapporteringsperioder.add(rapporteringsperiode)
        rapporteringsperiode.registrer(this)
    }

    fun behandle(hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler søknad innsendt")
        rapporteringsplikt.get(hendelse.opprettet).behandle(this, hendelse)
    }

    fun behandle(hendelse: RapporteringspliktDatoHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler dato for rapporteringsplikt")
        rapporteringsplikt.get(hendelse.opprettet).behandle(this, hendelse)
    }

    fun behandle(hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny rapporteringssyklus")

        rapporteringsplikt.get(hendelse.fom).behandle(this, hendelse)
    }

    fun behandle(hendelse: NyAktivitetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Tar imot ny aktivitet utført av bruker")

        rapporteringsperioder.behandle(hendelse) { it.behandle(hendelse) }
    }

    fun behandle(hendelse: SlettAktivitetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Sletter aktivitet utført av bruker")

        rapporteringsperioder.behandle(hendelse) { it.behandle(hendelse) }
    }

    fun behandle(hendelse: GodkjennPeriodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Behandler ny innrapportering")

        rapporteringsperioder.behandle(hendelse) { it.behandle(hendelse) }
    }

    fun behandle(hendelse: RapporteringsfristHendelse) {
        rapporteringsperioder.forEach { it.behandle(hendelse) }
    }

    fun behandle(hendelse: KorrigerPeriodeHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Korrigerer rapporteringsperiode")

        rapporteringsperioder.behandle(hendelse) { it.behandle(hendelse) }
    }

    fun behandle(hendelse: ManuellInnsendingHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Manuelt sender inn en rapporteringsperiode")

        rapporteringsperioder.behandle(hendelse) { it.behandle(hendelse) }
    }

    fun registrer(observer: PersonObserver) {
        observers.add(observer)
    }

    override fun rapporteringsperiodeEndret(event: RapporteringsperiodeObserver.RapporteringsperiodeEndret) {
        observers.forEach { it.rapporteringsperiodeEndret(event) }
    }

    override fun rapporteringsperiodeInnsendt(event: RapporteringsperiodeObserver.RapporteringsperiodeInnsendt) {
        observers.forEach { it.rapporteringsperiodeInnsendt(event) }
    }

    override fun toSpesifikkKontekst() = SpesifikkKontekst("person", mapOf("ident" to ident))
    override fun equals(other: Any?) = other is Person && this.ident == other.ident

    override fun hashCode() = this.ident.hashCode()

    fun accept(visitor: PersonVisitor) {
        visitor.visit(this, ident)
        rapporteringsperioder.accept(visitor)
        rapporteringsplikt.accept(visitor)
    }
}

private fun Collection<Rapporteringsperiode>.accept(visitor: PersonVisitor) = forEach { it.accept(visitor) }
private fun Collection<Rapporteringsperiode>.behandle(
    hendelse: PersonHendelse,
    block: (Rapporteringsperiode) -> Boolean,
) {
    if (none { block(it) }) {
        hendelse.logiskFeil("Ingen rapporteringsperiode håndterte aktiviteten")
    }
}
