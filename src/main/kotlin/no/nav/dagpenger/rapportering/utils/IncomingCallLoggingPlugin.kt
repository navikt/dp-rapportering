package no.nav.dagpenger.rapportering.utils

import io.ktor.http.content.OutputStreamContent
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseBodyReadyForSend
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.host
import io.ktor.server.request.httpMethod
import io.ktor.server.request.httpVersion
import io.ktor.server.request.path
import io.ktor.server.request.port
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readUTF8LineTo
import io.ktor.utils.io.writer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration.MDC_CORRELATION_ID
import no.nav.dagpenger.rapportering.Configuration.NO_LOG_PATHS
import no.nav.dagpenger.rapportering.api.auth.optionalIdent
import no.nav.dagpenger.rapportering.model.KallLogg
import no.nav.dagpenger.rapportering.repository.KallLoggRepository
import org.slf4j.MDC
import java.time.Instant
import java.time.LocalDateTime

val IncomingCallLoggingPlugin: ApplicationPlugin<ICDLPConfig> =
    createApplicationPlugin("IncomingCallLoggingPlugin", ::ICDLPConfig) {

        val logger = KotlinLogging.logger {}

        val kallLoggRepository = pluginConfig.kallLoggRepository
        val kallLoggIdAttr = AttributeKey<Long>("kallLoggId")
        var body = ""

        onCall { call ->
            if (NO_LOG_PATHS.firstOrNull { call.request.path().startsWith(it) } != null) {
                return@onCall
            }

            val ident = call.optionalIdent() ?: ""

            val requestData =
                StringBuilder()
                    .apply {
                        val request = call.request

                        appendLine("${request.httpMethod.value} ${request.host()}:${request.port()}${request.uri} ${request.httpVersion}")

                        request.headers.forEach { header, values ->
                            appendLine("$header: ${headersToString(values)}")
                        }

                        // empty line before body as in HTTP request
                        appendLine()

                        // body
                        appendLine(call.receiveText())
                    }.toString()

            try {
                val kallLoggId =
                    kallLoggRepository.lagreKallLogg(
                        KallLogg(
                            korrelasjonId = getCallId(),
                            tidspunkt = LocalDateTime.now(),
                            type = "REST",
                            kallRetning = "INN",
                            method = call.request.httpMethod.value,
                            operation = call.request.path(),
                            status = 0,
                            kallTid = Instant.now().toEpochMilli(),
                            request = requestData,
                            response = "",
                            ident = ident,
                            logginfo = "",
                        ),
                    )
                call.attributes.put(kallLoggIdAttr, kallLoggId)
            } catch (e: Exception) {
                logger.error("Kunne ikke lagre kall logg", e)
            }
        }

        on(ResponseBodyReadyForSend) { call, content ->
            if (NO_LOG_PATHS.firstOrNull { call.request.path().startsWith(it) } != null) {
                return@on
            }

            body = readBody(content)
        }

        on(ResponseSent) { call ->
            if (NO_LOG_PATHS.firstOrNull { call.request.path().startsWith(it) } != null) {
                return@on
            }

            val kallLoggId = call.attributes[kallLoggIdAttr]

            val responseData =
                StringBuilder()
                    .apply {
                        val response = call.response

                        appendLine("${response.status()?.value} ${response.status()?.description}")

                        response.headers.allValues().forEach { header, values ->
                            appendLine("$header: ${headersToString(values)}")
                        }

                        // empty line before body as in HTTP response
                        appendLine()

                        // body
                        appendLine(body)
                    }.toString()

            kallLoggRepository.lagreResponse(kallLoggId, call.response.status()?.value ?: 0, responseData)
        }
    }

class ICDLPConfig {
    lateinit var kallLoggRepository: KallLoggRepository
}

fun getCallId(): String {
    var korrelasjonId = MDC.get(MDC_CORRELATION_ID)

    if (korrelasjonId == null || korrelasjonId.isBlank()) {
        korrelasjonId = generateCallId()
        MDC.put(MDC_CORRELATION_ID, korrelasjonId)
    }

    // DB has max 54 signs in the korrelasjon_id field, so we must not have more otherwise we will get SQL error
    if (korrelasjonId.length > 54) {
        korrelasjonId = korrelasjonId.substring(0, 54)
    }

    return korrelasjonId
}

private fun readBody(subject: Any): String =
    when (subject) {
        is TextContent -> subject.text
        is OutputStreamContent -> {
            val channel = ByteChannel(true)
            runBlocking {
                GlobalScope.writer(coroutineContext, autoFlush = true) {
                    subject.writeTo(channel)
                }
                val buffer = StringBuilder()
                while (!channel.isClosedForRead) {
                    channel.readUTF8LineTo(buffer)
                }
                buffer.toString()
            }
        }

        else -> String()
    }
