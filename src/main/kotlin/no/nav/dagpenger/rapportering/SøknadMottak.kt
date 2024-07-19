package no.nav.dagpenger.rapportering

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import java.util.UUID

class SøknadMottak(
    rapidsConnection: RapidsConnection,
    private val mediator: Mediator,
) : River.PacketListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    init {
        logger.info { "Initierer SøknadMottak!" }
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "innsending_ferdigstilt") }
            validate { it.demandAny("type", listOf("NySøknad")) }
            validate { it.requireKey("fødselsnummer") }
            validate { it.require("søknadsData") { data -> data["søknad_uuid"].asUUID() } }
            validate { it.interestedIn("@id", "@opprettet") }
        }
        logger.info { "Init av SøknadMottak er ferdig!" }
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
    ) {
        logger.info { "Mottok søknad" }
        val ident = packet["fødselsnummer"].asText()
        val søknadId = packet["søknadsData"]["søknad_uuid"].asUUID()

        withLoggingContext("søknadId" to søknadId.toString()) {
            val søknadInnsendtMelding = SøknadInnsendtMelding(packet, ident, søknadId)
            søknadInnsendtMelding.behandle(mediator, context)

            logger.info { "Fått SøknadInnsendtHendelse for $søknadId. Packet: ${packet.toJson()}" }
        }
    }
}

private fun JsonNode.asUUID(): UUID = this.asText().let { UUID.fromString(it) }

internal class SøknadInnsendtMelding(
    packet: JsonMessage,
    override val ident: String,
    private val søknadId: UUID,
) : HendelseMessage(packet) {
    private val søknadInnsendtHendelse: SoknadInnsendtHendelse
        get() {
            return SoknadInnsendtHendelse(id, ident, opprettet, søknadId)
        }

    override fun behandle(
        mediator: Mediator,
        context: MessageContext,
    ) {
        mediator.behandle(søknadInnsendtHendelse)
    }
}

internal abstract class HendelseMessage(
    private val packet: JsonMessage,
) {
    internal val id: UUID = UUID.fromString(packet["@id"].asText())
    private val navn = packet["@event_name"].asText()
    protected val opprettet = packet["@opprettet"].asLocalDateTime()
    internal open val skalDuplikatsjekkes = true
    internal abstract val ident: String

    internal abstract fun behandle(
        mediator: Mediator,
        context: MessageContext,
    )

    /* internal fun lagreMelding(repository: HendelseRepository) {
        repository.lagreMelding(this, ident, id, toJson())
    }*/

    internal fun logReplays(
        logger: Logger,
        size: Int,
    ) {
        logger.info("som følge av $navn id=$id sendes $size meldinger for replay for fnr=$ident")
    }

    internal fun logOutgoingMessages(
        logger: Logger,
        size: Int,
    ) {
        logger.info("som følge av $navn id=$id sendes $size meldinger på rapid for fnr=$ident")
    }

    internal fun logRecognized(
        insecureLog: Logger,
        safeLog: Logger,
    ) {
        insecureLog.info("gjenkjente {} med id={}", this::class.simpleName, id)
        safeLog.info("gjenkjente {} med id={} for fnr={}:\n{}", this::class.simpleName, id, ident, toJson())
    }

    internal fun logDuplikat(logger: Logger) {
        logger.warn("Har mottatt duplikat {} med id={} for fnr={}", this::class.simpleName, id, ident)
    }

    internal fun secureDiagnosticinfo() =
        mapOf(
            "fødselsnummer" to ident,
        )

    internal fun tracinginfo() =
        additionalTracinginfo(packet) +
            mapOf(
                "event_name" to navn,
                "id" to id,
                "opprettet" to opprettet,
            )

    protected open fun additionalTracinginfo(packet: JsonMessage): Map<String, Any> = emptyMap()

    internal fun toJson() = packet.toJson()
}
