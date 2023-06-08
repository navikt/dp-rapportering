package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import java.util.UUID

interface Rapporteringsplikt : Aktivitetskontekst {
    val uuid: UUID
    val type: RapporteringspliktType
    fun behandle(person: Person, hendelse: SøknadInnsendtHendelse)
    fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse)
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

class RapporteringspliktSøknad(override val uuid: UUID = UUID.randomUUID()) : Rapporteringsplikt {
    override val type: RapporteringspliktType
        get() = RapporteringspliktType.Søknad

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode på grunn av innsendt søknad")

        Rapporteringsperiode(hendelse.fom).also { periode ->
            periode.gjelderFra.datesUntil(hendelse.fom).forEach {
                periode.leggTilFritak(it)
            }

            person.leggTilRapporteringsperiode(periode, hendelse)
        }
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
) : Rapporteringsplikt {
    override val type: RapporteringspliktType
        get() = RapporteringspliktType.Vedtak

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {}
    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }
}

class IngenRapporteringsplikt(override val uuid: UUID = UUID.randomUUID()) : Rapporteringsplikt {
    override val type: RapporteringspliktType
        get() = RapporteringspliktType.Ingen

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter rapporteringsplikt")

        person.rapporteringsplikt = RapporteringspliktSøknad()
        person.behandle(hendelse)
    }

    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {}
}
