package no.nav.dagpenger.rapportering.tjenester

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.MineBehov
import no.nav.dagpenger.rapportering.meldinger.RapporteringJournalførtMelding
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River

internal class RapporteringJournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: IHendelseMediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall.${this::class.java.simpleName}")
    }

    private val behov = MineBehov.JournalføreRapportering.name

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "behov") }
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.requireKey("ident", "periodeId", "@løsning") }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required(behov)
                }
            }
            validate { it.requireValue("@final", true) }
            validate { it.interestedIn("@id", "@opprettet") }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        val ident = packet["ident"].asText()
        val periodeId = packet["periodeId"].asText()
        val journalpostId = packet["@løsning"]["journalpostId"].asText()

        withLoggingContext(
            "periodeId" to periodeId,
        ) {
            val melding =
                RapporteringJournalførtMelding(
                    packet,
                    ident,
                    periodeId,
                    journalpostId,
                )
            melding.behandle(mediator, context)

            logger.info { "Fått løsning for JournalføreRapportering for $periodeId" }
            sikkerlogg.info { "Fått løsning for JournalføreRapportering for $periodeId. Packet: ${packet.toJson()}" }
        }
    }
}
