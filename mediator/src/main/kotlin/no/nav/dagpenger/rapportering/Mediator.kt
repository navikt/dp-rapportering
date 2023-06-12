package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.NyRapporteringssyklusHendelse
import no.nav.dagpenger.rapportering.hendelser.PersonHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.PersonRepository
import no.nav.helse.rapids_rivers.RapidsConnection

private val sikkerlogg = KotlinLogging.logger("tjenestekall.Mediator")

internal class Mediator(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
) : IHendelseMediator, PersonRepository by personRepository {
    // TODO - override fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext) {
    override fun behandle(hendelse: SøknadInnsendtHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: NyAktivitetHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: SlettAktivitetHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: GodkjennPeriodeHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: RapporteringsfristHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: NyRapporteringssyklusHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    override fun behandle(hendelse: KorrigerPeriodeHendelse) {
        hentPersonOgHåndter(hendelse.ident(), hendelse) { person ->
            person.behandle(hendelse)
        }
    }

    private fun <Hendelse : PersonHendelse> hentPersonOgHåndter(
        ident: String,
        hendelse: Hendelse,
        handler: (Person) -> Unit,
    ) {
        val person = hentEllerOpprettPerson(ident)
        person.registrer(UtsendingsObserver(rapidsConnection, hendelse))
        handler(person)
        finalize(person, hendelse)
    }

    private fun finalize(person: Person, hendelse: PersonHendelse) {
        lagre(person)
        if (!hendelse.harAktiviteter()) return
        if (hendelse.harFunksjonelleFeilEllerVerre()) {
            sikkerlogg.info("aktivitetslogg inneholder feil:\n${hendelse.toLogString()}")
        } else {
            sikkerlogg.info("aktivitetslogg inneholder meldinger:\n${hendelse.toLogString()}")
        }
        // TODO: behovMediator.håndter(context, hendelse)
    }
}
