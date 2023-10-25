package no.nav.dagpenger.rapportering.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.MineBehov
import no.nav.dagpenger.rapportering.meldinger.RapporteringMellomlagretMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class RapporteringMellomlagretMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.${this::class.java.simpleName}")
    }

    private val behov = MineBehov.MellomlagreRapportering.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.requireKey("ident", "@løsning") }
            validate {
                it.require(behov) { behov ->
                    behov.required("periodeId")
                    behov.required("json")
                }
            }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
            validate { it.interestedIn("@id", "@opprettet") }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val ident = packet["ident"].asText()
        val periodeId = packet[behov]["periodeId"].asText()
        val json = packet[behov]["json"].asText()
        val urn = packet["@løsning"][behov][0]["urn"].asText()

        withLoggingContext(
            "periodeId" to periodeId,
        ) {
            val melding =
                RapporteringMellomlagretMelding(
                    packet,
                    ident,
                    periodeId,
                    json,
                    urn,
                )
            melding.behandle(mediator, context)

            logger.info { "Fått løsning for MellomlagreRapportering for $periodeId" }
            sikkerlogg.info { "Fått løsning for MellomlagreRapportering for $periodeId. Packet: ${packet.toJson()}" }
        }
    }
}
