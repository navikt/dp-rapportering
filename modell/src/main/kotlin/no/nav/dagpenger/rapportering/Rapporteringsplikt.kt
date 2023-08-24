package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import java.time.LocalDateTime
import java.util.UUID

interface Rapporteringsplikt : Aktivitetskontekst {
    val uuid: UUID
    val type: RapporteringspliktType
    val rapporteringspliktFra: LocalDateTime
    fun behandle(person: Person, hendelse: SøknadInnsendtHendelse)
    fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse)
    fun behandle(person: Person, hendelse: RapporteringspliktDatoHendelse) {
        throw IllegalStateException("Forventer ikke ${RapporteringspliktDatoHendelse::class.java.simpleName}")
    }

    fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse)
    fun behandle(hendelse: BeregningsdatoPassertHendelse, rapporteringsperioder: List<Rapporteringsperiode>) {}

    override fun toSpesifikkKontekst() = SpesifikkKontekst("Rapporteringsplikt", mapOf("type" to type.name))
    fun accept(visitor: RapporteringspliktVisitor) {
        visitor.visit(this, this.uuid, this.type)
    }
}

enum class RapporteringspliktType {
    Ingen,
    Søknad,
    Vedtak,
}

class IngenRapporteringsplikt(
    override val uuid: UUID = UUID.randomUUID(),
    override val rapporteringspliktFra: LocalDateTime = LocalDateTime.now(),
) : Rapporteringsplikt {
    override val type = RapporteringspliktType.Ingen

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.behov(
            MineBehov.Søknadstidspunkt,
            "Trenger søknadstidspunkt for å opprette rapporteringsplikt",
            mapOf(
                "søknad_uuid" to hendelse.søknadId,
            ),
        )
    }

    override fun behandle(person: Person, hendelse: RapporteringspliktDatoHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter rapporteringsplikt på grunn av søknad")

        person.nyRapporteringsplikt(
            RapporteringspliktSøknad(rapporteringspliktFra = hendelse.søknadInnsendtDato.atStartOfDay()).also {
                it.behandle(person, hendelse)
            },
        )
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter rapporteringsplikt på grunn av vedtak")

        person.nyRapporteringsplikt(
            RapporteringspliktVedtak(
                rapporteringspliktFra = hendelse.virkningsdato.atStartOfDay(),
                sakId = hendelse.sakId,
            ).also {
                it.behandle(person, hendelse)
            },
        )
    }

    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {}
}

class RapporteringspliktSøknad(
    override val uuid: UUID = UUID.randomUUID(),
    override val rapporteringspliktFra: LocalDateTime,
) : Rapporteringsplikt {
    override val type = RapporteringspliktType.Søknad

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Har allerede rapporteringsplikt, oppretter ikke ny")
    }

    override fun behandle(person: Person, hendelse: RapporteringspliktDatoHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode på grunn av innsendt søknad")

        Rapporteringsperiode(rapporteringspliktFra.toLocalDate(), hendelse.beregningsdatoStrategi).also { periode ->
            periode.gjelderFra.datesUntil(rapporteringspliktFra.toLocalDate()).forEach {
                periode.leggTilFritak(it)
            }

            person.leggTilRapporteringsperiode(periode, hendelse)
        }
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Innvilgelse gir person rapporteringsplikt type Vedtak.")

        person.nyRapporteringsplikt(RapporteringspliktVedtak(rapporteringspliktFra = hendelse.virkningsdato.atStartOfDay(), sakId = hendelse.sakId))
    }

    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom, hendelse.beregningsdatoStrategi).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }
}

class RapporteringspliktVedtak(
    override val uuid: UUID = UUID.randomUUID(),
    override val rapporteringspliktFra: LocalDateTime,
    private val sakId: UUID,
) : Rapporteringsplikt {
    override val type = RapporteringspliktType.Vedtak

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {}
    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom, hendelse.beregningsdatoStrategi).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode på grunn av vedtak")

        Rapporteringsperiode(rapporteringspliktFra.toLocalDate(), hendelse.beregningsdatoStrategi).also { periode ->
            periode.gjelderFra.datesUntil(rapporteringspliktFra.toLocalDate()).forEach {
                periode.leggTilFritak(it)
            }

            person.leggTilRapporteringsperiode(periode, hendelse)
        }
    }

    override fun behandle(hendelse: BeregningsdatoPassertHendelse, rapporteringsperioder: List<Rapporteringsperiode>) {
        rapporteringsperioder.filter {
            it.gjelderFor(rapporteringspliktFra)
        }.forEach {
            it.behandle(hendelse, sakId)
        }
    }
}
