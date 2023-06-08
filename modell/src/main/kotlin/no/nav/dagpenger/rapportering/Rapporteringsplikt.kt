package no.nav.dagpenger.rapportering

import no.nav.dagpenger.aktivitetslogg.Aktivitetskontekst
import no.nav.dagpenger.aktivitetslogg.SpesifikkKontekst
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse

interface Rapporteringsplikt : Aktivitetskontekst {
    val type: Rapporteringsplikttype
    fun behandle(person: Person, hendelse: SøknadInnsendtHendelse)
    fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse)
    override fun toSpesifikkKontekst() = SpesifikkKontekst("Rapporteringsplikt", mapOf("type" to type.name))
}

enum class Rapporteringsplikttype {
    Ingen,
    Søknad,
    Vedtak,
}

class RapporteringspliktSøknad : Rapporteringsplikt {
    override val type: Rapporteringsplikttype
        get() = Rapporteringsplikttype.Søknad

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

class RapporteringspliktVedtak(private val normalFastsattArbeidstid: Double) : Rapporteringsplikt {
    override val type: Rapporteringsplikttype
        get() = Rapporteringsplikttype.Vedtak

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {}
    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter ny rapporteringsperiode")

        Rapporteringsperiode(hendelse.fom).also {
            person.leggTilRapporteringsperiode(it, hendelse)
        }
    }
}

class IngenRapporteringsplikt : Rapporteringsplikt {
    override val type: Rapporteringsplikttype
        get() = Rapporteringsplikttype.Ingen

    override fun behandle(person: Person, hendelse: SøknadInnsendtHendelse) {
        hendelse.kontekst(this)
        hendelse.info("Oppretter rapporteringsplikt")

        person.rapporteringsplikt = RapporteringspliktSøknad()
        person.behandle(hendelse)
    }
    override fun behandle(person: Person, hendelse: NyRapporteringssyklusHendelse) {}
}
