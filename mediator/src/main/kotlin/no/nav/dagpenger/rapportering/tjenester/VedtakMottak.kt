package no.nav.dagpenger.rapportering.tjenester

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.meldinger.VedtakMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class VedtakMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.${this::class.java.simpleName}")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.demandAny("@event_name", listOf("dagpenger_innvilget", "dagpenger_avslått")) }
            validate { it.requireKey("ident", "behandlingId", "virkningsdato", "sakId") }
            validate { it.interestedIn("@id", "@opprettet") }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val ident = packet["ident"].asText()
        val behandlingId = packet["behandlingId"].asUUID()

        withLoggingContext(
            "behandlingId" to behandlingId.toString(),
        ) {
            val vedtakMelding = VedtakMelding(packet, ident, behandlingId)
            vedtakMelding.behandle(mediator, context)

            logger.info { "Fått vedtak for behandling med id: $behandlingId" }
            sikkerlogg.info { "Fått vedtak for behandling med id: $behandlingId. Packet: ${packet.toJson()}" }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
    ) {
        logger.info { "${this.javaClass.simpleName} kunne ikke lese melding: \n $problems" }
    }

    private fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
}
