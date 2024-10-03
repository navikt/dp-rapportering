package no.nav.dagpenger.rapportering.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.model.MineBehov
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository

internal class RapporteringJournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val journalfoeringRepository: JournalfoeringRepository,
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
            validate { it.requireKey("ident", "@løsning") }
            validate {
                it.require(behov) { behov ->
                    behov.required("periodeId")
                }
            }
            validate {
                it.require("@løsning") { løsning ->
                    løsning.required("journalpostId")
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
        // TODO: Kall logg
        val periodeId = packet[behov]["periodeId"].asText()
        val journalpostId = packet["@løsning"]["journalpostId"].asLong()

        withLoggingContext(
            "periodeId" to periodeId,
        ) {
            logger.info { "Fått løsning for JournalføreRapportering for $periodeId" }
            sikkerlogg.info { "Fått løsning for JournalføreRapportering for $periodeId. Packet: ${packet.toJson()}" }

            runBlocking {
                journalfoeringRepository.lagreJournalpostData(journalpostId, 0L, periodeId.toLong())
            }
        }
    }
}
