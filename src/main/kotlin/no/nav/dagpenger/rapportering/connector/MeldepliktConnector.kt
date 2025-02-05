package no.nav.dagpenger.rapportering.connector

import com.fasterxml.jackson.core.type.TypeReference
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Person
import java.net.URI
import kotlin.time.measureTime

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    private val tokenProvider: (String) -> String? = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    private val httpClient: HttpClient,
    private val actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")

    suspend fun harMeldeplikt(
        ident: String,
        subjectToken: String,
    ): String =
        withContext(Dispatchers.IO) {
            val result =
                get("/harmeldeplikt", subjectToken, "adapter-harMeldeplikt")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente meldeplikt ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente meldeplikt for $ident ga status ${it.status}" }
                    }

            if (result.status == HttpStatusCode.OK) {
                result.bodyAsText()
            } else {
                throw Exception("Uforventet HTTP status ${result.status.value} ved henting av meldeplikt")
            }
        }

    suspend fun hentRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            val result =
                get("/rapporteringsperioder", subjectToken, "adapter-hentRapporteringsperioder")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente perioder ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente perioder for $ident ga status ${it.status}" }
                    }

            if (result.status == HttpStatusCode.NoContent) {
                null
            } else {
                result
                    .bodyAsText()
                    .let {
                        val perioder =
                            defaultObjectMapper.readValue(
                                it,
                                object : TypeReference<List<AdapterRapporteringsperiode>>() {},
                            )
                        if (perioder.isEmpty()) {
                            null
                        } else {
                            perioder
                        }
                    }
            }
        }

    suspend fun hentPerson(
        ident: String,
        subjectToken: String,
    ): Person? =
        withContext(Dispatchers.IO) {
            val result =
                get("/person", subjectToken, "adapter-hentPerson")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente person ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente person $ident ga status ${it.status}" }
                    }

            if (result.status == HttpStatusCode.NoContent) {
                null
            } else {
                result
                    .bodyAsText()
                    .let { defaultObjectMapper.readValue(it, Person::class.java) }
            }
        }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            hentData<String>("/sendterapporteringsperioder", subjectToken, "adapter-hentInnsendteRapporteringsperioder")
                .let {
                    val perioder =
                        defaultObjectMapper.readValue(
                            it,
                            object : TypeReference<List<AdapterRapporteringsperiode>>() {},
                        )
                    if (perioder.isEmpty()) {
                        null
                    } else {
                        perioder
                    }
                }.also {
                    logger.info { "Kall til meldeplikt-adapter for å hente innsendte perioder gikk OK" }
                    sikkerlogg.info { "Kall til meldeplikt-adapter for å hente innsendte perioder for $ident gikk OK" }
                }
        }

    suspend fun hentAktivitetsdager(
        id: String,
        subjectToken: String,
    ): List<AdapterDag> =
        hentData<List<AdapterDag>>("/aktivitetsdager/$id", subjectToken, "adapter-hentAktivitetsdager")
            .also {
                logger.info { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" }
            }

    suspend fun hentEndringId(
        id: Long,
        subjectToken: String,
    ): String =
        withContext(Dispatchers.IO) {
            hentData<String>("/endrerapporteringsperiode/$id", subjectToken, "adapter-hentEndringId")
                .also { logger.info { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" } }
        }

    suspend fun sendinnRapporteringsperiode(
        rapporteringsperiode: AdapterRapporteringsperiode,
        subjectToken: String,
    ): InnsendingResponse =
        withContext(Dispatchers.IO) {
            logger.info { "Rapporteringsperiode som sendes til adapter: $rapporteringsperiode" }
            logger.info { "Meldeplikt-url: $meldepliktUrl" }
            try {
                sendData("/sendinn", subjectToken, "adapter-sendinnRapporteringsperiode", rapporteringsperiode)
                    .also { logger.info { "Kall til meldeplikt-adapter for å sende inn rapporteringsperiode ga status ${it.status}" } }
                    .bodyAsText()
                    .let { defaultObjectMapper.readValue(it, InnsendingResponse::class.java) }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved sending av data til meldeplikt-adapter" }
                throw e
            }
        }

    private suspend inline fun <reified T> hentData(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
    ): T =
        try {
            get(path, subjectToken, metrikkNavn)
                .body<T>()
        } catch (e: Exception) {
            logger.error(e) { "Feil ved henting av data fra meldeplikt-adapter. Path: $path" }
            throw e
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
                    httpClient.get(URI("$meldepliktUrl$path").toURL()) {
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
                    httpClient.post(URI("$meldepliktUrl$path").toURL()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(defaultObjectMapper.writeValueAsString(body))
                    }
            }
        actionTimer.httpTimer(metrikkNavn, response.status, HttpMethod.Post, tidBrukt.inWholeSeconds)
        return response
    }
}
