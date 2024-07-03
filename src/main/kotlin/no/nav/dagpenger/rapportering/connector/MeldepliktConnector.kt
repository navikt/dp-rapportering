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
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Person
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    val tokenProvider: (String) -> String = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    val httpClient: HttpClient,
) {
    suspend fun hentRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            val result =
                get("/rapporteringsperioder", subjectToken)
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente perioder gikk OK" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente perioder for $ident gikk OK" }
                    }

            if (result.status == HttpStatusCode.NoContent) {
                null
            } else {
                result
                    .bodyAsText()
                    .let {
                        Configuration.defaultObjectMapper.readValue(it, object : TypeReference<List<AdapterRapporteringsperiode>>() {})
                    }
            }
        }

    suspend fun hentPerson(
        ident: String,
        subjectToken: String,
    ): Person? =
        withContext(Dispatchers.IO) {
            val result =
                get("/person", subjectToken)
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente person gikk OK" }
                        sikkerlogg.info { "Kall til meldeplikt-adapter for å hente person $ident gikk OK" }
                    }

            if (result.status == HttpStatusCode.NoContent) {
                null
            } else {
                result
                    .bodyAsText()
                    .let { Configuration.defaultObjectMapper.readValue(it, Person::class.java) }
            }
        }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode> =
        withContext(Dispatchers.IO) {
            hentData<List<AdapterRapporteringsperiode>>("/sendterapporteringsperioder", subjectToken)
                .also {
                    logger.info { "Kall til meldeplikt-adapter for å hente innsendte perioder gikk OK" }
                    sikkerlogg.info { "Kall til meldeplikt-adapter for å hente innsendte perioder for $ident gikk OK" }
                }
        }

    suspend fun hentAktivitetsdager(
        id: String,
        subjectToken: String,
    ): List<AdapterDag> =
        hentData<List<AdapterDag>>("/aktivitetsdager/$id", subjectToken)
            .also {
                logger.info { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" }
            }

    suspend fun hentKorrigeringId(
        id: Long,
        subjectToken: String,
    ): String =
        withContext(Dispatchers.IO) {
            hentData<String>("/korrigerrapporteringsperiode/$id", subjectToken)
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
                sendData("/sendinn", subjectToken, rapporteringsperiode)
                    .also { logger.info { "Kall til meldeplikt-adapter for å sende inn rapporteringsperiode gikk OK" } }
                    .bodyAsText()
                    .let { Configuration.defaultObjectMapper.readValue(it, InnsendingResponse::class.java) }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved sending av data til meldeplikt-adapter" }
                throw e
            }
        }

    private suspend inline fun <reified T> hentData(
        path: String,
        subjectToken: String,
    ): T =
        try {
            get(path, subjectToken)
                .body<T>()
        } catch (e: Exception) {
            logger.error(e) { "Feil ved henting av data fra meldeplikt-adapter. Path: $path" }
            throw e
        }

    private suspend fun get(
        path: String,
        subjectToken: String,
    ): HttpResponse =
        httpClient.get(URI("$meldepliktUrl$path").toURL()) {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
            contentType(ContentType.Application.Json)
        }

    private suspend fun sendData(
        path: String,
        subjectToken: String,
        body: Any?,
    ): HttpResponse =
        httpClient.post(URI("$meldepliktUrl$path").toURL()) {
            header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
            contentType(ContentType.Application.Json)
            setBody(Configuration.defaultObjectMapper.writeValueAsString(body))
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
