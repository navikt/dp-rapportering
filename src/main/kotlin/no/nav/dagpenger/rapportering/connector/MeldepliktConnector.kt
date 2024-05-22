package no.nav.dagpenger.rapportering.connector

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    val tokenProvider: (String) -> String = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    engine: HttpClientEngine = CIO.create {},
) {
    val httpClient = createHttpClient(engine)

    suspend fun hentMeldekort(
        ident: String,
        subjectToken: String,
    ): List<Rapporteringsperiode> =
        // TODO Returtype: List<Rapporteringsperiode> -> Person
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse =
                    httpClient.get(URI("$meldepliktUrl/meldekort/$ident").toURL()) {
                        header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
                        contentType(ContentType.Application.Json)
                    }
                if (response.status.isSuccess()) {
                    logger.info { "Kall til meldeplikt-adapter gikk OK" }
                    sikkerlogg.info { "Kall til meldeplikt-adapter for å hente perioder for $ident gikk OK" }
                    response.body()
                } else {
                    logger.warn { "Kall til meldeplikt-adapter feilet med status ${response.status}" }
                    MeldepliktMetrikker.meldepliktError.inc()
                    emptyList()
                }
            } catch (e: Exception) {
                logger.warn(e) { "Kall til meldeplikt-adapter eller mapping av response feilet" }
                MeldepliktMetrikker.meldepliktException.inc()
                emptyList()
            }
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
