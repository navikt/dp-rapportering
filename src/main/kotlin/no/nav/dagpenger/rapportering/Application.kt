package no.nav.dagpenger.rapportering

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.dagpenger.rapportering.Configuration.appMicrometerRegistry
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.konfigurasjon
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.connector.DokarkivConnector
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.jobs.SlettRapporteringsperioderJob
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.preparePartitions
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.RapporteringService

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module(httpClient: HttpClient = createHttpClient(CIO.create {})) {
    preparePartitions()

    val meldepliktConnector = MeldepliktConnector(httpClient = httpClient)

    val rapporteringService =
        RapporteringService(
            meldepliktConnector,
            RapporteringRepositoryPostgres(dataSource),
            JournalfoeringService(
                meldepliktConnector,
                DokarkivConnector(httpClient = httpClient),
                JournalfoeringRepositoryPostgres(dataSource),
            ),
        )
    konfigurasjon(appMicrometerRegistry)
    internalApi(appMicrometerRegistry)
    rapporteringApi(rapporteringService)

    SlettRapporteringsperioderJob.start(rapporteringService)
}
