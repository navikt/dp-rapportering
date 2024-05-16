package no.nav.dagpenger.rapportering.connector

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    engine: HttpClientEngine = CIO.create {},
) {
    val httpClient = createHttpClient(engine)

    suspend fun hentMeldekort(ident: String): List<Rapporteringsperiode> =
        withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse =
                    httpClient.get(URI("$meldepliktUrl/meldekort/$ident").toURL()) {
                        contentType(ContentType.Application.Json)
                    }
                if (response.status.isSuccess()) {
                    logger.info("Kall til meldeplikt-adapter gikk OK")
                    response.body()
                } else {
                    logger.warn("Kall til meldeplikt-adapter feilet med status ${response.status}")
                    emptyList()
                }
            } catch (e: Exception) {
                logger.warn("Kall til meldeplikt-adapter eller mapping av response feilet", e)
                emptyList()
            }
        }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
