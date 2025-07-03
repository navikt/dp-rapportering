package no.nav.dagpenger.rapportering.service

import com.fasterxml.jackson.core.type.TypeReference
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.connector.HttpClientUtils
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.PeriodeData

class MeldekortregisterService(
    meldekortregisterUrl: String = Configuration.meldekortregisterUrl,
    tokenProvider: (String) -> String? = Configuration.tokenXClient(Configuration.meldekortregisterAudience),
    httpClient: HttpClient,
    actionTimer: ActionTimer,
) {
    private val logger = KotlinLogging.logger {}
    private val sikkerlogg = KotlinLogging.logger("tjenestekall.MeldekortregisterService")
    private val httpClientUtils = HttpClientUtils(httpClient, meldekortregisterUrl, tokenProvider, actionTimer)

    suspend fun hentRapporteringsperioder(
        ident: String,
        token: String,
    ): List<PeriodeData>? =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get("/meldekort", token, "meldekortregister-hentMeldekort")
                    .also {
                        logger.info { "Kall til meldekortregister for å hente perioder ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldekortregister for å hente perioder for ident $ident ga status ${it.status}" }
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
                                object : TypeReference<List<PeriodeData>>() {},
                            )
                        perioder.ifEmpty {
                            null
                        }
                    }
            }
        }

    suspend fun hentEndringId(
        id: String,
        token: String,
    ): String =
        withContext(Dispatchers.IO) {
            val result =
                httpClientUtils
                    .get("/endrerapporteringsperiode/$id", token, "meldekortregister-hentEndringId")
                    .also {
                        logger.info { "Kall til meldekortregister for å hente endringId ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldekortregister for å hente endringId for periode $id ga status ${it.status}" }
                    }

            result.bodyAsText()
        }

    suspend fun sendinnRapporteringsperiode(
        rapporteringsperiode: PeriodeData,
        token: String,
    ): InnsendingResponse =
        withContext(Dispatchers.IO) {
            val id = rapporteringsperiode.id

            val result =
                httpClientUtils
                    .post("/meldekort", token, "meldekortregister-sendInnMeldekort", rapporteringsperiode)
                    .also {
                        logger.info { "Kall til meldekortregister for å sende periode ga status ${it.status}" }
                        sikkerlogg.info { "Kall til meldekortregister for å sende periode $id ga status ${it.status}" }
                    }

            val status =
                if (result.status == HttpStatusCode.OK) {
                    "OK"
                } else {
                    "FEIL"
                }

            InnsendingResponse(id, status, emptyList())
        }
}
