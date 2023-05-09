package no.nav.dagpenger.rapportering.meldinger

import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import java.util.UUID

internal abstract class HendelseMessage(private val packet: JsonMessage) {
    internal val id: UUID = UUID.fromString(packet["@id"].asText())
    private val navn = packet["@event_name"].asText()
    protected val opprettet = packet["@opprettet"].asLocalDateTime()
    internal open val skalDuplikatsjekkes = true
    protected abstract val ident: String

    internal abstract fun behandle(mediator: IHendelseMediator, context: MessageContext)

    internal fun lagreMelding(repository: HendelseRepository) {
        repository.lagreMelding(this, ident, id, toJson())
    }

    internal fun logReplays(logger: Logger, size: Int) {
        logger.info("som følge av $navn id=$id sendes $size meldinger for replay for fnr=$ident")
    }

    internal fun logOutgoingMessages(logger: Logger, size: Int) {
        logger.info("som følge av $navn id=$id sendes $size meldinger på rapid for fnr=$ident")
    }

    internal fun logRecognized(insecureLog: Logger, safeLog: Logger) {
        insecureLog.info("gjenkjente {} med id={}", this::class.simpleName, id)
        safeLog.info("gjenkjente {} med id={} for fnr={}:\n{}", this::class.simpleName, id, ident, toJson())
    }

    internal fun logDuplikat(logger: Logger) {
        logger.warn("Har mottatt duplikat {} med id={} for fnr={}", this::class.simpleName, id, ident)
    }

    internal fun secureDiagnosticinfo() = mapOf(
        "fødselsnummer" to ident,
    )

    internal fun tracinginfo() = additionalTracinginfo(packet) + mapOf(
        "event_name" to navn,
        "id" to id,
        "opprettet" to opprettet,
    )

    protected open fun additionalTracinginfo(packet: JsonMessage): Map<String, Any> = emptyMap()

    internal fun toJson() = packet.toJson()
}
