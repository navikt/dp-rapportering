package no.nav.dagpenger.rapportering.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.utils.Sikkerlogg
import kotlin.time.measureTime

class HttpClientUtils(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val tokenProvider: (String) -> String?,
    private val actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}

    suspend fun get(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
    ): HttpResponse {
        val response: HttpResponse

        try {
            val token = tokenProvider.invoke(subjectToken) ?: throw RuntimeException("Fant ikke token")
            val tidBrukt =
                measureTime {
                    response =
                        httpClient.get("$baseUrl$path") {
                            bearerAuth(token)
                            contentType(ContentType.Application.Json)
                        }
                }
            actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Get, tidBrukt.inWholeSeconds)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved GET URL $baseUrl$path" }
            Sikkerlogg.error(e) { "Feil ved GET URL $baseUrl$path" }
            throw e
        }

        return response
    }

    suspend fun post(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
        contentType: ContentType,
        body: Any?,
    ): HttpResponse {
        val response: HttpResponse

        try {
            val token = tokenProvider.invoke(subjectToken) ?: throw RuntimeException("Fant ikke token")
            val tidBrukt =
                measureTime {
                    response =
                        httpClient.post("$baseUrl$path") {
                            bearerAuth(token)
                            contentType(contentType)
                            setBody(defaultObjectMapper.writeValueAsString(body))
                        }
                }
            actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Post, tidBrukt.inWholeSeconds)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved POST URL $baseUrl$path" }
            Sikkerlogg.error(e) { "Feil ved POST URL $baseUrl$path" }
            throw e
        }

        return response
    }

    suspend fun post(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
        body: Any?,
    ): HttpResponse =
        post(
            path,
            subjectToken,
            metrikkNavn,
            ContentType.Application.Json,
            body,
        )
}
