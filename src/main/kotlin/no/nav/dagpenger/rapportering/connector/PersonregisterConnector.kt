package no.nav.dagpenger.rapportering.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PersonregisterConnector(
    val personregisterUrl: String = Configuration.personregisterUrl,
    val tokenXProvider: (String) -> String? = Configuration.tokenXClient(Configuration.personregisterAudience),
    val azureTokenProvider: () -> String = Configuration.personregisterTokenProvider,
    val httpClient: HttpClient,
    actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    private val httpClientUtils = HttpClientUtils(httpClient, personregisterUrl, tokenXProvider, actionTimer)

    suspend fun hentPersonstatus(
        ident: String,
        subjectToken: String,
    ): Personstatus? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get(
                        "/personstatus",
                        subjectToken,
                        "personregister-hentPersonstatus",
                    ).also {
                        logger.info { "Kall til personregister for å hente personstatus ga status ${it.status}" }
                        sikkerlogg.info { "Kall til personregister for å hente personstatus for $ident ga status ${it.status}" }
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
                httpClientUtils
                    .post(
                        "/personstatus",
                        subjectToken,
                        "personregister-oppdaterPersonstatus",
                        ContentType.Text.Plain,
                        datoFra.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    ).also {
                        logger.info { "Kall til personregister for å oppdatere personstatus ga status ${it.status}" }
                        sikkerlogg.info { "Kall til personregister for å oppdatere personstatus for $ident ga status ${it.status}" }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved sending av data til personregister" }
                throw e
            }
        }

    suspend fun hentSisteSakId(ident: String): String? =
        withContext(Dispatchers.IO) {
            try {
                logger.info { "Henter siste sakId fra personregister" }
                sikkerlogg.info { "Henter siste sakId fra personregister for ident $ident" }

                val response =
                    httpClient
                        .post("$personregisterUrl/hentSisteSakId") {
                            bearerAuth(
                                azureTokenProvider.invoke(),
                            )
                            contentType(ContentType.Application.Json)
                            setBody(defaultObjectMapper.writeValueAsString(IdentBody(ident)))
                        }.also {
                            sikkerlogg.info {
                                "Kall til personregister for å hente siste sakId for $ident ga status ${it.status}"
                            }
                        }

                if (response.status != HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    logger.error { "Klarte ikke å hente siste sakId fra personregister, status: ${response.status}, melding: $body" }
                    sikkerlogg.error { "Klarte ikke å hente siste sakId for ident $ident, status: ${response.status}, melding: $body" }
                    return@withContext null
                }

                response
                    .body<SakIdBody>()
                    .sakId
                    .also {
                        logger.info { "Hentet siste sakId fra personregister" }
                        sikkerlogg.info { "Hentet siste sakId $it for ident $ident fra personregister" }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Kunne ikke hente siste sakId" }
                throw RuntimeException(e)
            }
        }
}

data class Personstatus(
    val ident: String,
    val status: Brukerstatus,
    val overtattBekreftelse: Boolean,
    val ansvarligSystem: AnsvarligSystem?,
)

data class IdentBody(
    val ident: String,
)

data class SakIdBody(
    val sakId: String,
)

enum class Brukerstatus {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}

enum class AnsvarligSystem {
    ARENA,
    DP,
}
