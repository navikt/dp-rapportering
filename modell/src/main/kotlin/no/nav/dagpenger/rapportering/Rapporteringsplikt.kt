package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
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
        hendelse.behov(MineBehov.Virkningsdatoer, "Trenger virkningsdatoer for å opprette rapporteringsplikt")
        hendelse.behov(MineBehov.Innsendingstidspunkt, "Trenger innsendingstidspunkt for å opprette rapporteringsplikt")
    }

    override fun behandle(person: Person, hendelse: RapporteringspliktDatoHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter rapporteringsplikt")

        person.nyRapporteringsplikt(
            RapporteringspliktSøknad(rapporteringspliktFra = hendelse.gjelderFra.atStartOfDay()).also {
                it.behandle(person, hendelse)
            },
        )
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        throw IllegalStateException("Kan ikke behandle vedtak for person uten rapporteringsplikt")
    }

    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {}
}

class RapporteringspliktSøknad(override val uuid: UUID = UUID.randomUUID(), override val rapporteringspliktFra: LocalDateTime) :
    Rapporteringsplikt {
    override val type = RapporteringspliktType.Søknad

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Har allerede rapporteringsplikt, oppretter ikke ny")
    }

    override fun behandle(person: Person, hendelse: RapporteringspliktDatoHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode på grunn av innsendt søknad")

        Rapporteringsperiode(rapporteringspliktFra.toLocalDate()).also { periode ->
            periode.gjelderFra.datesUntil(rapporteringspliktFra.toLocalDate()).forEach {
                periode.leggTilFritak(it)
            }

            person.leggTilRapporteringsperiode(periode, hendelse)
        }
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Innvilgelse gir person rapporteringsplikt type Vedtak.")

        person.nyRapporteringsplikt(RapporteringspliktVedtak(rapporteringspliktFra = hendelse.virkningsdato.atStartOfDay()))
    }

    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }
}

class RapporteringspliktVedtak(
    override val uuid: UUID = UUID.randomUUID(),
    override val rapporteringspliktFra: LocalDateTime,
) : Rapporteringsplikt {
    override val type = RapporteringspliktType.Vedtak

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {}
    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }

    override fun behandle(person: Person, hendelse: VedtakInnvilgetHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Har allerede rapporteringsplikt type Vedtak.")
    }
}
