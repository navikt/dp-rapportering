package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext

internal class SøknadInnsendtMelding(packet: JsonMessage, override val ident: String) : HendelseMessage(packet) {

    private val søknadInnsendtHendelse get() = SøknadInnsendtHendelse(id, ident)
    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        mediator.behandle(this, søknadInnsendtHendelse, context)
    }
}
