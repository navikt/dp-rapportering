package no.nav.dagpenger.rapportering.connector

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.JournalpostResponse
import java.net.URI
import kotlin.time.measureTime

class DokarkivConnector(
    private val dokarkivUrl: String = Configuration.dokarkivUrl,
    private val tokenProvider: (String) -> String? = Configuration.azureADClient(),
    private val httpClient: HttpClient,
    private val actionTimer: ActionTimer,
) {
    private val path = "/rest/journalpostapi/v1/journalpost"

    suspend fun sendJournalpost(journalpost: Journalpost): JournalpostResponse {
        val response: HttpResponse
        val tidBrukt =
            measureTime {
                val token =
                    tokenProvider.invoke("api://${Configuration.dokarkivAudience}/.default") ?: throw RuntimeException("Fant ikke token")

                logger.info("Prøver å sende journalpost " + journalpost.eksternReferanseId)

                response =
                    httpClient
                        .post(URI("$dokarkivUrl$path").toURL()) {
                            bearerAuth(token)
                            accept(ContentType.Application.Json)
                            contentType(ContentType.Application.Json)
                            setBody(defaultObjectMapper.writeValueAsString(journalpost))
                        }
            }
        actionTimer.httpTimer("dokarkiv-sendJournalpost", response.status, HttpMethod.Post, tidBrukt.inWholeSeconds)
        logger.info("Journalpost sendt. Svar " + response.status)
        return response
            .bodyAsText()
            .let {
                defaultObjectMapper.readValue(it, JournalpostResponse::class.java)
            }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
