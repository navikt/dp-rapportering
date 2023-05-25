package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.dagpenger.rapportering.repository.AktivitetRepository
import no.nav.dagpenger.rapportering.repository.PersonRepository
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection

internal class Mediator(
    rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val aktivitetRepository: AktivitetRepository,
) : IHendelseMediator, PersonRepository by personRepository {
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
