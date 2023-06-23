package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.VedtakHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import java.util.UUID

internal class VedtakMelding(packet: JsonMessage, override val ident: String, private val behandlingId: UUID) :
    HendelseMessage(packet) {

    private val virkningsdato = packet["virkningsdato"].asLocalDate()
    private val utfall = packet["utfall"].asText()

    private val vedtakHendelse: VedtakHendelse
        get() {
            return VedtakHendelse(id, ident)
        }

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        TODO("Not yet implemented")
    }
}
