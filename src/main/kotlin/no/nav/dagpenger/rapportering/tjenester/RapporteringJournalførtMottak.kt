package no.nav.dagpenger.rapportering.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.dagpenger.rapportering.model.MineBehov
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepository
import no.nav.dagpenger.rapportering.repository.KallLoggRepository

internal class RapporteringJournalførtMottak(
    rapidsConnection: RapidsConnection,
    private val journalfoeringRepository: JournalfoeringRepository,
    private val kallLoggRepository: KallLoggRepository,
) : River.PacketListener {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.RapporteringJournalførtMottak")

    private val behov = MineBehov.JournalføreRapportering.name

    init {
        River(rapidsConnection)
            .apply {
                precondition { it.requireValue("@event_name", "behov") }
                precondition { it.requireAll("@behov", listOf(behov)) }
                validate { it.requireKey("@løsning") }
                validate { it.requireValue("@final", true) }
                validate {
                    it.require(behov) { behov ->
                        behov.required("periodeId")
                        behov.required("kallLoggId")
                    }
                }
                validate {
                    it.require("@løsning") { løsning ->
                        løsning.required(behov)
                    }
                }
                validate { it.interestedIn("@id", "@opprettet", "@behovId") }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val behovId = packet["@behovId"].asText()
        val periodeId = packet[behov]["periodeId"].asText()
        val kallLoggId = packet[behov]["kallLoggId"].asLong()
        val journalpostId = packet["@løsning"][behov].asLong()

        packet[behov] = "SE TILSVARENDE REQUEST" // Bare for å spare litt plass i vår DB

        withLoggingContext(
            "behovId" to behovId,
            "periodeId" to periodeId,
        ) {
            if (packet["@id"].asText() in listOf("84478f4a-f27d-4dcb-adf4-b627c7a03872")) {
                logger.warn {
                    "Ignorerer melding fordi den inneholder en duplikat journalføring, periodeId=$periodeId, journalpostId=$journalpostId"
                }
                return
            }

            logger.info { "Fått løsning for JournalføreRapportering for $periodeId" }
            sikkerlogg.info { "Fått løsning for JournalføreRapportering for $periodeId. Packet: ${packet.toJson()}" }

            runBlocking {
                journalfoeringRepository.lagreJournalpostData(journalpostId, 0L, periodeId.toLong())
                kallLoggRepository.lagreResponse(kallLoggId, 200, packet.toJson())
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        logger.info { problems.toExtendedReport() }
    }
}
