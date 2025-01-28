package no.nav.dagpenger.rapportering

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.config.konfigurasjon
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.jobs.RapporterDatabaseMetrikkerJob
import no.nav.dagpenger.rapportering.jobs.SlettRapporteringsperioderJob
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.KallLoggRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.preparePartitions
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.KallLoggService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.tjenester.RapporteringJournalførtMottak
import no.nav.helse.rapids_rivers.RapidApplication
import io.ktor.server.cio.CIO as CIOEngine

class ApplicationBuilder(
    configuration: Map<String, String>,
    httpClient: HttpClient = createHttpClient(CIO.create {}),
) : RapidsConnection.StatusListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private lateinit var rapidsConnection: RapidsConnection

        fun getRapidsConnection(): RapidsConnection {
            return rapidsConnection
        }
    }

    private val meterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val meldepliktMetrikker = MeldepliktMetrikker(meterRegistry)
    private val databaseMetrikker = DatabaseMetrikker(meterRegistry)
    private val actionTimer = ActionTimer(meterRegistry)

    private val slettRapporteringsperioderJob = SlettRapporteringsperioderJob(meterRegistry, httpClient)
    private val rapporterDatabaseMetrikker = RapporterDatabaseMetrikkerJob(databaseMetrikker)

    private val meldepliktConnector = MeldepliktConnector(httpClient = httpClient, actionTimer = actionTimer)
    private val rapporteringRepository = RapporteringRepositoryPostgres(dataSource, actionTimer)
    private val innsendingtidspunktRepository = InnsendingtidspunktRepositoryPostgres(dataSource, actionTimer)
    private val journalfoeringRepository = JournalfoeringRepositoryPostgres(dataSource, actionTimer)
    private val kallLoggRepository = KallLoggRepositoryPostgres(dataSource)

    private val kallLoggService = KallLoggService(kallLoggRepository)

    private val journalfoeringService =
        JournalfoeringService(
            journalfoeringRepository,
            kallLoggService,
            httpClient,
            meterRegistry,
        )

    private val rapporteringService =
        RapporteringService(
            meldepliktConnector,
            rapporteringRepository,
            innsendingtidspunktRepository,
            journalfoeringService,
            kallLoggService,
        )

    init {
        JvmInfoMetrics().bindTo(meterRegistry)

        rapidsConnection =
            RapidApplication
                .create(
                    env = configuration,
                    builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
                ) { engine, _: RapidsConnection ->
                    engine.application.konfigurasjon(meterRegistry)
                    engine.application.internalApi(meterRegistry)
                    engine.application.rapporteringApi(rapporteringService, meldepliktMetrikker)
                }
        rapidsConnection.register(this)
        RapporteringJournalførtMottak(rapidsConnection, journalfoeringRepository, kallLoggRepository)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logger.info { "Starter dp-rapportering" }

        preparePartitions().also { logger.info { "Startet jobb for behandling av partisjoner" } }
        slettRapporteringsperioderJob
            .start(rapporteringService)
            .also {
                logger.info { "Startet jobb for sletting av rapporteringsperioder" }
            }
        rapporterDatabaseMetrikker
            .start(rapporteringRepository, journalfoeringRepository)
            .also {
                logger.info { "Startet jobb for rapportering av metrikker" }
            }
    }
}
