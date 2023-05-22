package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection

internal class Mediator(rapidsConnection: RapidsConnection) : IHendelseMediator {
    override fun behandle(nyAktivitetHendelse: NyAktivitetHendelse) {
        val person = hentPerson(nyAktivitetHendelse.ident())
        person.behandle(nyAktivitetHendelse)
    }

    private fun hentPerson(ident: String) = Person(ident)

    override fun behandle(melding: SøknadInnsendtMelding, hendelse: SøknadInnsendtHendelse, context: MessageContext) {
        TODO("Not yet implemented")
    }
}
