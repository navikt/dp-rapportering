package no.nav.dagpenger.rapportering.connector

import com.fasterxml.jackson.core.type.TypeReference
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.utils.Sikkerlogg

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    tokenProvider: (String) -> String? = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    httpClient: HttpClient,
    actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val httpClientUtils = HttpClientUtils(httpClient, meldepliktUrl, tokenProvider, actionTimer)

    suspend fun harMeldeplikt(
        ident: String,
        subjectToken: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val result = httpClientUtils.get("/harmeldeplikt", subjectToken, "adapter-harMeldeplikt")

            when (result.status) {
                HttpStatusCode.OK -> {
                    val harMeldeplikt = result.bodyAsText()

                    logger.info { "Bruker har meldeplikt: $harMeldeplikt" }
                    Sikkerlogg.info { "Bruker med ident $ident har meldeplikt: $harMeldeplikt" }

                    harMeldeplikt.toBoolean()
                }

                else -> {
                    val melding = result.bodyAsText()
                    Sikkerlogg.error {
                        "Uventet status fra meldeplikt-adapter for harMeldeplikt for $ident: ${result.status} - $melding"
                    }
                    throw RuntimeException(
                        "Uventet status fra meldeplikt-adapter for harMeldeplikt: ${result.status}",
                    )
                }
            }
        }

    suspend fun hentRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get("/rapporteringsperioder", subjectToken, "adapter-hentRapporteringsperioder")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente perioder ga status ${it.status}" }
                        Sikkerlogg.info { "Kall til meldeplikt-adapter for å hente perioder for $ident ga status ${it.status}" }
                    }

            lesPerioder(result)
        }

    suspend fun hentPerson(
        ident: String,
        subjectToken: String,
    ): Person? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get("/person", subjectToken, "adapter-hentPerson")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente person ga status ${it.status}" }
                        Sikkerlogg.info { "Kall til meldeplikt-adapter for å hente person $ident ga status ${it.status}" }
                    }

            when (result.status) {
                HttpStatusCode.NoContent -> {
                    null
                }

                HttpStatusCode.OK -> {
                    result
                        .bodyAsText()
                        .let { defaultObjectMapper.readValue(it, Person::class.java) }
                }

                else -> {
                    val melding = result.bodyAsText()
                    Sikkerlogg.error {
                        "Uforventet status ved henting av person fra adapter for $ident: ${result.status.value} - $melding"
                    }
                    throw RuntimeException(
                        "Uforventet status ved henting av person fra adapter: ${result.status.value}",
                    )
                }
            }
        }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get("/sendterapporteringsperioder", subjectToken, "adapter-hentInnsendteRapporteringsperioder")
                    .also {
                        logger.info { "Kall til meldeplikt-adapter for å hente innsendte perioder ga status ${it.status}" }
                        Sikkerlogg.info { "Kall til meldeplikt-adapter for å hente innsendte perioder for $ident ga status ${it.status}" }
                    }

            lesPerioder(result)
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
        id: String,
        subjectToken: String,
    ): String =
        withContext(Dispatchers.IO) {
            hentData<String>("/endrerapporteringsperiode/$id", subjectToken, "adapter-hentEndringId")
                .also { logger.info { "Kall til meldeplikt-adapter for å hente endringId gikk OK" } }
        }

    suspend fun sendinnRapporteringsperiode(
        rapporteringsperiode: AdapterRapporteringsperiode,
        subjectToken: String,
    ): InnsendingResponse =
        withContext(Dispatchers.IO) {
            logger.info { "Rapporteringsperiode som sendes til adapter: $rapporteringsperiode" }
            logger.info { "Meldeplikt-url: $meldepliktUrl" }

            val result =
                httpClientUtils
                    .post("/sendinn", subjectToken, "adapter-sendinnRapporteringsperiode", rapporteringsperiode)
                    .also { logger.info { "Kall til meldeplikt-adapter for å sende inn rapporteringsperiode ga status ${it.status}" } }

            when (result.status) {
                HttpStatusCode.OK -> {
                    result.bodyAsText().let { defaultObjectMapper.readValue(it, InnsendingResponse::class.java) }
                }

                else -> {
                    val melding = result.bodyAsText()
                    Sikkerlogg.error {
                        "Uforventet status ved sending av rapporteringsperiode til adapter: ${result.status.value} - $melding"
                    }
                    throw RuntimeException(
                        "Uforventet status ved sending av rapporteringsperiode til adapter: ${result.status.value}",
                    )
                }
            }
        }

    private suspend inline fun <reified T> hentData(
        path: String,
        subjectToken: String,
        metrikkNavn: String,
    ): T {
        val result = httpClientUtils.get(path, subjectToken, metrikkNavn)
        return when (result.status) {
            HttpStatusCode.OK -> {
                result.body<T>()
            }

            else -> {
                val melding = result.bodyAsText()
                Sikkerlogg.error {
                    "Uforventet status fra meldeplikt-adapter for $path: ${result.status.value} - $melding"
                }
                throw RuntimeException(
                    "Uforventet status fra meldeplikt-adapter for $path: ${result.status.value}",
                )
            }
        }
    }

    private suspend fun lesPerioder(result: HttpResponse): List<AdapterRapporteringsperiode>? =
        when (result.status) {
            HttpStatusCode.NoContent -> {
                null
            }

            HttpStatusCode.OK -> {
                result.bodyAsText().let {
                    val perioder =
                        defaultObjectMapper.readValue(
                            it,
                            object : TypeReference<List<AdapterRapporteringsperiode>>() {},
                        )
                    perioder.ifEmpty { null }
                }
            }

            else -> {
                val melding = result.bodyAsText()
                Sikkerlogg.error {
                    "Uforventet status ved henting av rapporteringsperioder fra adapter: ${result.status.value} - $melding"
                }
                throw RuntimeException(
                    "Uforventet status ved henting av rapporteringsperioder fra adapter: ${result.status.value}",
                )
            }
        }
}
