package no.nav.dagpenger.rapportering.tjenester

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.meldinger.SøknadInnsendtMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

internal class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.SøknadMottak")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "innsending_ferdigstilt") }
            validate { it.demandAny("type", listOf("NySøknad")) }
            validate { it.requireKey("fødselsnummer") }
            validate {
                it.require("søknadsData") { data ->
                    data["søknad_uuid"].asUUID()
                }
            }
            validate { it.interestedIn("@id", "@opprettet") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["fødselsnummer"].asText()
        val søknadID = packet["søknadsData"]["søknad_uuid"].asUUID()
        withLoggingContext(
            "søknadId" to søknadID.toString(),
        ) {
            val søknadInnsendtMelding = SøknadInnsendtMelding(packet, ident)
            søknadInnsendtMelding.behandle(mediator, context)

            logger.info { "Fått SøknadInnsendtHendelse for $søknadID" }
            sikkerlogg.info { "Fått SøknadInnsendtHendelse for $søknadID. Packet: ${packet.toJson()}" }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.info { "${this.javaClass.simpleName} kunne ikke lese melding: \n $problems" }
    }

    private fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }
}
