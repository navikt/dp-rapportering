package no.nav.dagpenger.rapportering.connector

import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.modeller.Rapporteringsperiode
import java.net.URI

class MeldepliktConnector(
    private val meldepliktUrl: String = Configuration.meldepliktAdapterUrl,
    engine: HttpClientEngine = CIO.create {},
) {
    val httpClient = createHttpClient(engine)

    suspend fun hentMeldekort(ident: String): List<Rapporteringsperiode> {
        val response =
            httpClient.get(URI("$meldepliktUrl/meldekort/$ident").toURL()) {
                contentType(ContentType.Application.Json)
            }
        return response.body()
    }
}
