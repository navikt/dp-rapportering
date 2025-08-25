package no.nav.dagpenger.rapportering.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PersonregisterConnector(
    personregisterUrl: String = Configuration.personregisterUrl,
    tokenProvider: (String) -> String? = Configuration.tokenXClient(Configuration.personregisterAudience),
    httpClient: HttpClient,
    actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    private val httpClientUtils = HttpClientUtils(httpClient, personregisterUrl, tokenProvider, actionTimer)

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
                httpClientUtils
                    .post(
                        "/personstatus",
                        subjectToken,
                        "personregister-oppdaterPersonstatus",
                        ContentType.Text.Plain,
                        datoFra.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    ).also {
                        logger.info { "Kall til personregister for å sende personstatus ga status ${it.status}" }
                        sikkerlogg.info { "Kall til personregister for å sende personstatus for $ident ga status ${it.status}" }
                    }
            } catch (e: Exception) {
                logger.error(e) { "Feil ved sending av data til meldeplikt-adapter" }
                throw e
            }
        }
}

data class Personstatus(
    val ident: String,
    val status: Brukerstatus,
    val overtattBekreftelse: Boolean,
    val ansvarligSystem: AnsvarligSystem?,
)

enum class Brukerstatus {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}

enum class AnsvarligSystem {
    ARENA,
    DP,
}
