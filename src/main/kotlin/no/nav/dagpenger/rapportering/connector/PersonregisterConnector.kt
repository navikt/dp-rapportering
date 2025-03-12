package no.nav.dagpenger.rapportering.connector

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.measureTime

class PersonregisterConnector(
    private val personregisterUrl: String = Configuration.personregisterUrl,
    private val tokenProvider: (String) -> String? = Configuration.tokenXClient(Configuration.personregisterUrlAudience),
    private val httpClient: HttpClient,
    private val actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")

    suspend fun hentPersonstatus(
        ident: String,
        subjectToken: String,
    ): Personstatus? =
        withContext(Dispatchers.IO) {
            val result =
                get("/personstatus", subjectToken, "personregister-hentPersonstatus")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente person ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente person $ident ga status ${it.status}" }
                    }

            if (result.status == HttpStatusCode.NotFound) {
                null
            } else {
                result
                    .bodyAsText()
                    .let { defaultObjectMapper.readValue(it, Personstatus::class.java) }
            }
        }

    suspend fun hentBrukerstatus(
        ident: String,
        subjectToken: String,
    ): Brukerstatus =
        withContext(Dispatchers.IO) {
            val personstatus = hentPersonstatus(ident, subjectToken)

            personstatus?.status ?: Brukerstatus.IKKE_DAGPENGERBRUKER
        }

    suspend fun oppdaterPersonstatus(
        ident: String,
        subjectToken: String,
        datoFra: LocalDate,
    ): Unit =
        withContext(Dispatchers.IO) {
            try {
                sendData(
                    "/personstatus",
                    subjectToken,
                    "personregister-oppdaterPersonstatus",
                    datoFra.format(DateTimeFormatter.ISO_LOCAL_DATE),
                )
                    .also {
                        logger.info { "Kall til personregister for å sende personstatus ga status ${it.status}" }
                        sikkerlogg.info { "Kall til personregister for å sende personstatus for $ident ga status ${it.status}" }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved sending av data til meldeplikt-adapter" }
                throw e
            }
        }

    private suspend fun get(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
    ): HttpResponse {
        val response: HttpResponse
        val token = tokenProvider.invoke(subjectToken) ?: throw RuntimeException("Fant ikke token")
        val tidBrukt =
            measureTime {
                response =
                    httpClient.get(URI("$personregisterUrl$path").toURL()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                    }
            }
        actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Get, tidBrukt.inWholeSeconds)
        return response
    }

    private suspend fun sendData(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
        body: Any?,
    ): HttpResponse {
        val response: HttpResponse
        val token = tokenProvider.invoke(subjectToken) ?: throw RuntimeException("Fant ikke token")
        val tidBrukt =
            measureTime {
                response =
                    httpClient.post(URI("$personregisterUrl$path").toURL()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Text.Plain)
                        setBody(body)
                    }
            }
        actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Post, tidBrukt.inWholeSeconds)
        return response
    }
}

data class Personstatus(
    val ident: String,
    val status: Brukerstatus,
    val overtattBekreftelse: Boolean,
)

enum class Brukerstatus {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}
