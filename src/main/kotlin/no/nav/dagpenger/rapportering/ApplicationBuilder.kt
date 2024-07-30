package no.nav.dagpenger.rapportering

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.config.konfigurasjon
import no.nav.dagpenger.rapportering.connector.DokarkivConnector
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.jobs.RapporterDatabaseMetrikkerJob
import no.nav.dagpenger.rapportering.jobs.SlettRapporteringsperioderJob
import no.nav.dagpenger.rapportering.mediator.Mediator
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.preparePartitions
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

class ApplicationBuilder(
    configuration: Map<String, String>,
    httpClient: HttpClient = createHttpClient(CIO.create {}),
) : RapidsConnection.StatusListener {
    private companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val meldepliktConnector = MeldepliktConnector(httpClient = httpClient)
    private val rapporteringRepository = RapporteringRepositoryPostgres(dataSource)
    private val journalfoeringRepository = JournalfoeringRepositoryPostgres(dataSource)
    private val rapporteringService =
        RapporteringService(
            meldepliktConnector,
            rapporteringRepository,
            JournalfoeringService(
                meldepliktConnector,
                DokarkivConnector(httpClient = httpClient),
                journalfoeringRepository,
            ),
        )

    private val rapidsConnection =
        RapidApplication
            .Builder(RapidApplication.RapidApplicationConfig.fromEnv(configuration))
            .withKtorModule {
                konfigurasjon()
                internalApi()
                rapporteringApi(rapporteringService)
            }.build()

    private val mediator = Mediator(rapidsConnection)

    init {
        rapidsConnection.register(this)
        SøknadMottak(rapidsConnection, mediator)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logger.info { "Starter dp-rapportering" }

        preparePartitions().also { logger.info { "Startet jobb for behandling av partisjoner" } }
        SlettRapporteringsperioderJob
            .start(
                rapporteringService,
            ).also { logger.info { "Startet jobb for sletting av rapporteringsperioder" } }
        RapporterDatabaseMetrikkerJob.start(rapporteringRepository, journalfoeringRepository).also {
            logger.info { "Startet jobb for rapportering av metrikker" }
        }
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        logger.info { "Skrur av applikasjonen" }
    }
}
