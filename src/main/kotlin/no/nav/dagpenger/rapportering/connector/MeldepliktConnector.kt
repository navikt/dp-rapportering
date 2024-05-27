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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    val tokenProvider: (String) -> String = Configuration.tokenXClient(Configuration.meldepliktAdapterAudience),
    engine: HttpClientEngine = CIO.create {},
) {
    val httpClient = createHttpClient(engine)

    suspend fun hentRapporteringsperioder(
        ident: String,
        subjectToken: String,
    ): List<Rapporteringsperiode> =
        withContext(Dispatchers.IO) {
            val response: HttpResponse =
                httpClient.get(URI("$meldepliktUrl/rapporteringsperioder").toURL()) {
                    header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
                    contentType(ContentType.Application.Json)
                }

            logger.info { "Kall til meldeplikt-adapter for å hente perioder gikk OK" }
            sikkerlogg.info { "Kall til meldeplikt-adapter for å hente perioder for $ident gikk OK" }

            response.body()
        }

    suspend fun hentAktivitetsdager(
        id: String,
        subjectToken: String,
    ): List<Dag> =
        withContext(Dispatchers.IO) {
            val response: HttpResponse =
                httpClient.get(URI("$meldepliktUrl/aktivitetsdager/$id").toURL()) {
                    header(HttpHeaders.Authorization, "Bearer ${tokenProvider.invoke(subjectToken)}")
                    contentType(ContentType.Application.Json)
                }

            logger.info { "Kall til meldeplikt-adapter for å hente aktivitetsdager gikk OK" }

            response.body()
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
