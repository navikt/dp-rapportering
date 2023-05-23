package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.dagpenger.rapportering.repository.Repository
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection

internal class Mediator(rapidsConnection: RapidsConnection, private val repository: Repository) : IHendelseMediator, Repository by repository {
    override fun behandle(nyAktivitetHendelse: NyAktivitetHendelse) {
        val person = hentPerson(nyAktivitetHendelse.ident()) ?: throw IllegalStateException("Kan ikke behandle ny aktivitet for person som ikke er registrert")
        person.behandle(nyAktivitetHendelse)
    }

    private fun hentEllerOpprettPerson(ident: String) = hentPerson(ident) ?: Person(ident)

    override fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext) {
        // hente ØnskerDagpengerFraDato
        // hente SøknadsTidspunkt
        // hente eller lage Person
        // opprett rapporteringsperiode
        // person.behandle(periode)
        val person = hentEllerOpprettPerson(hendelse.ident())
        person.behandle(hendelse)
    }
}
