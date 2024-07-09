package no.nav.dagpenger.rapportering.connector

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.JournalpostResponse
import java.net.URI

class DokarkivConnector(
    private val dokarkivUrl: String = Configuration.dokarkivUrl,
    private val tokenProvider: (String) -> String = Configuration.azureADClient(),
    val httpClient: HttpClient,
) {
    private val path = "/rest/journalpostapi/v1/journalpost"

    suspend fun sendJournalpost(journalpost: Journalpost): JournalpostResponse {
        val token = tokenProvider.invoke("api://${Configuration.dokarkivAudience}/.default")

        logger.info("Prøver å sende journalpost " + journalpost.eksternReferanseId)

        val response =
            httpClient
                .post(URI("$dokarkivUrl$path").toURL()) {
                    bearerAuth(token)
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                    setBody(Configuration.defaultObjectMapper.writeValueAsString(journalpost))
                }

        logger.info("Journalpost sendt. Svar " + response.status)
        return response
            .bodyAsText()
            .let {
                Configuration.defaultObjectMapper.readValue(it, JournalpostResponse::class.java)
            }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
