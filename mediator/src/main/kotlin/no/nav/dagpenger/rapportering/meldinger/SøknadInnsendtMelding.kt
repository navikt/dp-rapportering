package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import java.util.UUID

internal class SøknadInnsendtMelding(packet: JsonMessage, override val ident: String, private val søknadId: UUID) :
    HendelseMessage(packet) {
    private val søknadInnsendtHendelse: SøknadInnsendtHendelse
        get() {
            return SøknadInnsendtHendelse(id, ident, opprettet, søknadId)
        }

    override fun behandle(
        mediator: IHendelseMediator,
        context: MessageContext,
    ) {
        mediator.behandle(søknadInnsendtHendelse)
    }
}
