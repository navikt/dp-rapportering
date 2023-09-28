package no.nav.dagpenger.rapportering.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.MineBehov
import no.nav.dagpenger.rapportering.meldinger.RapporteringMidlertidigJournalførtMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.toUUID

internal class NyJournalpostMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.SøknadMottak")
    }

    private val behov = MineBehov.JournalføreRapportering.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAllOrAny("@behov", listOf(behov)) }
            validate { it.requireKey("ident", "periodeId", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val ident = packet["ident"].asText()
        val periodeId = packet["periodeId"].asText()
        val journalpostId = packet["@løsning"]["journalpostId"].asText()
        val json = packet["@løsning"]["json"].asText()

        withLoggingContext(
            "periodeId" to periodeId,
        ) {
            val melding = RapporteringMidlertidigJournalførtMelding(
                packet,
                ident,
                periodeId.toUUID(),
                journalpostId,
                json,
            )
            melding.behandle(mediator, context)

            logger.info { "Fått RapporteringspliktDatoHendelse for $periodeId" }
            sikkerlogg.info { "Fått RapporteringspliktDatoHendelse for $periodeId. Packet: ${packet.toJson()}" }
        }
    }
}
