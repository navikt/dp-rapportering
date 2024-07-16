package no.nav.dagpenger.rapportering

import io.ktor.client.engine.cio.CIO
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.connector.createHttpClient

fun main() {
    // embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
    ApplicationBuilder(Configuration.config, createHttpClient(CIO.create {})).start()
}

/* fun Application.module(httpClient: HttpClient = createHttpClient(CIO.create {})) {
    preparePartitions()

    val meldepliktConnector = MeldepliktConnector(httpClient = httpClient)
    val rapporteringRepository = RapporteringRepositoryPostgres(dataSource)
    val journalfoeringRepository = JournalfoeringRepositoryPostgres(dataSource)

    val rapporteringService =
        RapporteringService(
            meldepliktConnector,
            rapporteringRepository,
            JournalfoeringService(
                meldepliktConnector,
                DokarkivConnector(httpClient = httpClient),
                journalfoeringRepository,
            ),
        )
    konfigurasjon(appMicrometerRegistry)
    internalApi(appMicrometerRegistry)
    rapporteringApi(rapporteringService)

    SlettRapporteringsperioderJob.start(rapporteringService)
    RapporterDatabaseMetrikkerJob.start(rapporteringRepository, journalfoeringRepository)
} */
