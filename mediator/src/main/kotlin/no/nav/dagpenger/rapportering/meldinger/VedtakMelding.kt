package no.nav.dagpenger.rapportering.meldinger

import com.fasterxml.jackson.databind.JsonNode
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.hendelser.VedtakAvslåttHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.strategiForBeregningsdato
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
    private val sakId = packet["sakId"].asUUID()

    override fun behandle(
        mediator: IHendelseMediator,
        context: MessageContext,
    ) {
        when (packet.eventNavn()) {
            "dagpenger_innvilget" ->
                mediator.behandle(
                    VedtakInnvilgetHendelse(id, ident, virkningsdato, opprettet, sakId, strategiForBeregningsdato),
                )
            "dagpenger_avslått" -> mediator.behandle(VedtakAvslåttHendelse(id, ident, virkningsdato, opprettet, sakId))
            else -> throw IllegalArgumentException("Ugyldig @event_navn, kan ikke mappe ${packet.eventNavn()}")
        }
    }

    private fun JsonMessage.eventNavn(): String = this["@event_name"].asText()

    private fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
}
