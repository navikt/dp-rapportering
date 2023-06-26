package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.VedtakAvslåttHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDate
import java.util.UUID

internal class VedtakMelding(
    private val packet: JsonMessage,
    override val ident: String,
    private val behandlingId: UUID,
) :
    HendelseMessage(packet) {

    private val virkningsdato = packet["virkningsdato"].asLocalDate()

    override fun behandle(mediator: IHendelseMediator, context: MessageContext) {
        when (packet.utfall()) {
            "Innvilget" -> mediator.behandle(VedtakInnvilgetHendelse(id, ident, virkningsdato))
            "Avslått" -> mediator.behandle(VedtakAvslåttHendelse(id, ident, virkningsdato))
            else -> throw IllegalArgumentException("Ugyldig utfall, kan ikke mappe ${packet.utfall()}")
        }
    }

    private fun JsonMessage.utfall(): String = this["utfall"].asText()
}
