package no.nav.dagpenger.rapportering.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.meldinger.RapporteringspliktDatoMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class RapporteringspliktDatoMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.${this::class.java.simpleName}")
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf("Virkningsdatoer", "Søknadstidspunkt")) }
            validate { it.requireKey("ident", "Søknadstidspunkt.søknad_uuid") }
            validate { it.requireKey("@løsning") }
            validate { it.requireValue("@final", true) }
            validate { it.interestedIn("@id", "@opprettet") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["ident"].asText()
        val søknadID = packet["Søknadstidspunkt.søknad_uuid"].asText()

        withLoggingContext(
            "søknadId" to søknadID.toString(),
        ) {
            val rapporteringspliktDatoMelding = RapporteringspliktDatoMelding(packet, ident)
            rapporteringspliktDatoMelding.behandle(mediator, context)

            logger.info { "Fått RapporteringspliktDatoHendelse for $søknadID" }
            sikkerlogg.info { "Fått RapporteringspliktDatoHendelse for $søknadID. Packet: ${packet.toJson()}" }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logger.info { "${this.javaClass.simpleName} kunne ikke lese melding: \n $problems" }
    }
}
