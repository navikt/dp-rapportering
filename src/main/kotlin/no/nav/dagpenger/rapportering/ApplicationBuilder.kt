package no.nav.dagpenger.rapportering

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.config.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.config.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.config.konfigurasjon
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.jobs.MidlertidigJournalføringJob
import no.nav.dagpenger.rapportering.jobs.RapporterDatabaseMetrikkerJob
import no.nav.dagpenger.rapportering.jobs.SlettRapporteringsperioderJob
import no.nav.dagpenger.rapportering.kafka.BekreftelseAvroSerializer
import no.nav.dagpenger.rapportering.kafka.KafkaFactory
import no.nav.dagpenger.rapportering.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.KallLoggRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.preparePartitions
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.KallLoggService
import no.nav.dagpenger.rapportering.service.MeldekortregisterService
import no.nav.dagpenger.rapportering.service.MeldepliktService
import no.nav.dagpenger.rapportering.service.PdlService
import no.nav.dagpenger.rapportering.service.PersonregisterService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.tjenester.RapporteringJournalførtMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.common.serialization.LongSerializer
import io.ktor.server.cio.CIO as CIOEngine

class ApplicationBuilder(
    configuration: Map<String, String>,
    httpClient: HttpClient = createHttpClient(CIO.create {}),
) : RapidsConnection.StatusListener {
    companion object {
        private val logger = KotlinLogging.logger {}
        private lateinit var rapidsConnection: RapidsConnection

        fun getRapidsConnection(): RapidsConnection = rapidsConnection
    }

    private val meterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    private val meldepliktMetrikker = MeldepliktMetrikker(meterRegistry)
    private val databaseMetrikker = DatabaseMetrikker(meterRegistry)
    private val actionTimer = ActionTimer(meterRegistry)

    private val slettRapporteringsperioderJob = SlettRapporteringsperioderJob(meterRegistry, httpClient)
    private val rapporterDatabaseMetrikker = RapporterDatabaseMetrikkerJob(databaseMetrikker)
    private val midlertidigJournalføringJob = MidlertidigJournalføringJob(httpClient, meterRegistry)

    private val meldepliktConnector = MeldepliktConnector(httpClient = httpClient, actionTimer = actionTimer)
    private val personregisterConnector = PersonregisterConnector(httpClient = httpClient, actionTimer = actionTimer)

    private val rapporteringRepository = RapporteringRepositoryPostgres(dataSource, actionTimer)
    private val innsendingtidspunktRepository = InnsendingtidspunktRepositoryPostgres(dataSource, actionTimer)
    private val journalfoeringRepository = JournalfoeringRepositoryPostgres(dataSource, actionTimer)
    private val kallLoggRepository = KallLoggRepositoryPostgres(dataSource)

    private val kallLoggService = KallLoggService(kallLoggRepository)
    private val pdlService = PdlService()

    private val journalfoeringService =
        JournalfoeringService(
            journalfoeringRepository,
            kallLoggService,
            pdlService,
            httpClient,
        )

    private val kafkaKonfigurasjon = KafkaKonfigurasjon(kafkaServerKonfigurasjon, kafkaSchemaRegistryConfig)
    private val kafkaFactory = KafkaFactory(kafkaKonfigurasjon)
    private val bekreftelseKafkaProdusent =
        kafkaFactory.createProducer<Long, Bekreftelse>(
            clientId = "teamdagpenger-rapportering-producer",
            keySerializer = LongSerializer::class,
            valueSerializer = BekreftelseAvroSerializer::class,
        )

    private val meldepliktService = MeldepliktService(meldepliktConnector)
    private val meldekortregisterService = MeldekortregisterService(httpClient = httpClient, actionTimer = actionTimer)
    private val personregisterService = PersonregisterService(personregisterConnector, meldepliktService)
    private val arbeidssøkerService = ArbeidssøkerService(kallLoggService, personregisterService, httpClient, bekreftelseKafkaProdusent)

    private val rapporteringService =
        RapporteringService(
            meldepliktService,
            rapporteringRepository,
            innsendingtidspunktRepository,
            journalfoeringService,
            kallLoggService,
            arbeidssøkerService,
            personregisterService,
            meldekortregisterService,
        )

    init {
        JvmInfoMetrics().bindTo(meterRegistry)

        rapidsConnection =
            RapidApplication
                .create(
                    env = configuration,
                    builder = { this.withKtor(embeddedServer(CIOEngine, port = 8080, module = {})) },
                ) { engine, _: RapidsConnection ->
                    engine.application.konfigurasjon(meterRegistry, kallLoggRepository)
                    engine.application.internalApi(meterRegistry)
                    engine.application.rapporteringApi(rapporteringService, personregisterService, meldepliktMetrikker)
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
        midlertidigJournalføringJob
            .start(journalfoeringService)
            .also {
                logger.info { "Startet jobb for midlertidig journalføring" }
            }
    }
}
