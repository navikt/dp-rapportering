package no.nav.dagpenger.rapportering.connector

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    val tokenProvider: (String) -> String = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    engine: HttpClientEngine = CIO.create {},
) {
    private val httpClient = createHttpClient(engine)

    suspend fun hentRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode>? =
        withContext(Dispatchers.IO) {
            val result =
                hentData("/rapporteringsperioder", subjectToken)
                    .loggInfo { "Kall til meldeplikt-adapter for å hente perioder gikk OK" }
                    .sikkerloggInfo { "Kall til meldeplikt-adapter for å hente perioder for $ident gikk OK" }

            if (result.status == HttpStatusCode.NoContent) {
                null
            } else {
                result.body()
            }
        }

    suspend fun hentInnsendteRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<AdapterRapporteringsperiode> =
        withContext(Dispatchers.IO) {
            hentData("/sendterapporteringsperioder", subjectToken)
                .loggInfo { "Kall til meldeplikt-adapter for å hente innsendte perioder gikk OK" }
                .sikkerloggInfo { "Kall til meldeplikt-adapter for å hente innsendte perioder for $ident gikk OK" }
                .body()
        }

    suspend fun hentAktivitetsdager(
        id: String,
        subjectToken: String,
    ): List<AdapterDag> =
        withContext(Dispatchers.IO) {
            hentData("/aktivitetsdager/$id", subjectToken)
                .loggInfo { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" }
                .body()
        }

    suspend fun hentKorrigeringId(
        id: Long,
        subjectToken: String,
    ): Long =
        withContext(Dispatchers.IO) {
            hentData("/korrigertMeldekort/$id", subjectToken)
                .loggInfo { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" }
                .body()
        }

    suspend fun sendinnRapporteringsperiode(
        rapporteringsperiode: AdapterRapporteringsperiode,
        subjectToken: String,
    ): InnsendingResponse =
        withContext(Dispatchers.IO) {
            logger.info { "Rapporteringsperiode som sendes til adapter: $rapporteringsperiode" }
            sendData("/sendinn", subjectToken, rapporteringsperiode)
                .loggInfo { "Kall til meldeplikt-adapter for å sende inn rapporteringsperiode gikk OK" }
                .body()
        }

    private suspend fun hentData(
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
            setBody(body)
        }

    private fun HttpResponse.loggInfo(msg: () -> String) = this.also { logger.info { msg } }

    private fun HttpResponse.sikkerloggInfo(msg: () -> String) = this.also { sikkerlogg.info { msg } }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
