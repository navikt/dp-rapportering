package no.nav.dagpenger.rapportering

import com.github.navikt.tbd_libs.naisful.naisApp
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationStopped
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.config.Configuration.config
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.config.Configuration.kafkaSchemaRegistryConfig
import no.nav.dagpenger.rapportering.config.Configuration.kafkaServerKonfigurasjon
import no.nav.dagpenger.rapportering.config.pluginConfiguration
import no.nav.dagpenger.rapportering.config.statusPagesConfig
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.connector.PersonregisterConnector
import no.nav.dagpenger.rapportering.connector.createHttpClient
import no.nav.dagpenger.rapportering.jobs.MidlertidigJournalføringJob
import no.nav.dagpenger.rapportering.jobs.RapporterDatabaseMetrikkerJob
import no.nav.dagpenger.rapportering.jobs.ScheduledTask
import no.nav.dagpenger.rapportering.jobs.SendBekreftelsesmeldingerJob
import no.nav.dagpenger.rapportering.jobs.SlettRapporteringsperioderJob
import no.nav.dagpenger.rapportering.jobs.TaskExecutor
import no.nav.dagpenger.rapportering.kafka.BekreftelseAvroSerializer
import no.nav.dagpenger.rapportering.kafka.KafkaFactory
import no.nav.dagpenger.rapportering.kafka.KafkaKonfigurasjon
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.metrics.DatabaseMetrikker
import no.nav.dagpenger.rapportering.metrics.JobbkjøringMetrikker
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.repository.BekreftelsesmeldingRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.KallLoggRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.preparePartitions
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.TidspunktjusteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.KallLoggService
import no.nav.dagpenger.rapportering.service.MeldekortregisterService
import no.nav.dagpenger.rapportering.service.MeldepliktService
import no.nav.dagpenger.rapportering.service.PdlService
import no.nav.dagpenger.rapportering.service.PersonregisterService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.tjenester.MeldekortJournalførtMottak
import no.nav.dagpenger.rapportering.tjenester.RapporteringJournalførtMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.common.serialization.LongSerializer
import org.slf4j.LoggerFactory

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
    private val actionTimer = ActionTimer(meterRegistry)

    private val meldepliktConnector = MeldepliktConnector(httpClient = httpClient, actionTimer = actionTimer)
    private val personregisterConnector = PersonregisterConnector(httpClient = httpClient, actionTimer = actionTimer)

    private val rapporteringRepository = RapporteringRepositoryPostgres(dataSource, actionTimer)
    private val tidspunktjusteringRepository = TidspunktjusteringRepositoryPostgres(dataSource, actionTimer)
    private val bekreftelsesmeldingRepository = BekreftelsesmeldingRepositoryPostgres(dataSource, actionTimer)
    private val journalfoeringRepository = JournalfoeringRepositoryPostgres(dataSource, actionTimer)
    private val kallLoggRepository = KallLoggRepositoryPostgres(dataSource)

    private val kallLoggService = KallLoggService(kallLoggRepository)
    private val pdlService = PdlService()

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
    private val personregisterService = PersonregisterService(personregisterConnector)
    private val arbeidssøkerService =
        ArbeidssøkerService(kallLoggService, personregisterService, httpClient, bekreftelseKafkaProdusent)

    private val journalfoeringService =
        JournalfoeringService(
            journalfoeringRepository,
            kallLoggService,
            pdlService,
            personregisterService,
            httpClient,
        )

    private val rapporteringService =
        RapporteringService(
            meldepliktService,
            rapporteringRepository,
            tidspunktjusteringRepository,
            bekreftelsesmeldingRepository,
            journalfoeringService,
            arbeidssøkerService,
            personregisterService,
            meldekortregisterService,
        )

    private val rapporterDatabaseMetrikkerJob =
        RapporterDatabaseMetrikkerJob(
            DatabaseMetrikker(meterRegistry),
            JobbkjøringMetrikker(
                meterRegistry,
                RapporterDatabaseMetrikkerJob::class.simpleName!!,
            ),
        )
    private val midlertidigJournalføringJob =
        MidlertidigJournalføringJob(
            httpClient,
            jobbkjøringMetrikker = JobbkjøringMetrikker(meterRegistry, MidlertidigJournalføringJob::class.simpleName!!),
        )
    private val sendBekreftelsesmeldingerJob =
        SendBekreftelsesmeldingerJob(
            httpClient,
            bekreftelsesmeldingRepository,
            rapporteringRepository,
            arbeidssøkerService,
            jobbkjøringMetrikker =
                JobbkjøringMetrikker(
                    meterRegistry,
                    SendBekreftelsesmeldingerJob::class.simpleName!!,
                ),
        )
    private val slettRapporteringsperioderJob =
        SlettRapporteringsperioderJob(
            httpClient,
            rapporteringService,
            jobbkjøringMetrikker =
                JobbkjøringMetrikker(
                    meterRegistry,
                    SlettRapporteringsperioderJob::class.simpleName!!,
                ),
        )
    private val taskExecutor =
        TaskExecutor(
            listOf(
                ScheduledTask(slettRapporteringsperioderJob, 0, 50),
            ),
        )

    init {
        JvmInfoMetrics().bindTo(meterRegistry)

        rapidsConnection =
            RapidApplication
                .create(
                    env = configuration,
                    builder = {
                        withKtor { preStopHook, rapid ->
                            naisApp(
                                meterRegistry = meterRegistry,
                                objectMapper = defaultObjectMapper,
                                applicationLogger = LoggerFactory.getLogger("ApplicationLogger"),
                                callLogger = LoggerFactory.getLogger("CallLogger"),
                                callIdHeaderName = HttpHeaders.XRequestId,
                                preStopHook = preStopHook::handlePreStopRequest,
                                aliveCheck = rapid::isReady,
                                readyCheck = rapid::isReady,
                                statusPagesConfig = { statusPagesConfig(meldepliktMetrikker) },
                            ) {
                                monitor.subscribe(ApplicationStopped) {
                                    logger.info { "Forsøker å lukke datasource..." }
                                    dataSource.close()
                                    logger.info { "Lukket datasource" }
                                }

                                pluginConfiguration(kallLoggRepository)
                                internalApi(meterRegistry)
                                rapporteringApi(
                                    rapporteringService,
                                    meldepliktService,
                                    journalfoeringService,
                                    meldepliktMetrikker,
                                )
                            }
                        }
                    },
                ) { _, _ ->
                    logger.info { "Starter rapid with config: $config" }
                }
        rapidsConnection.register(this)
        RapporteringJournalførtMottak(rapidsConnection, journalfoeringRepository, kallLoggRepository)
        MeldekortJournalførtMottak(rapidsConnection, journalfoeringRepository, kallLoggRepository)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logger.info { "Starter dp-rapportering" }

        preparePartitions().also { logger.info { "Startet jobb for behandling av partisjoner" } }

        rapporterDatabaseMetrikkerJob
            .start(rapporteringRepository, journalfoeringRepository)
            .also {
                logger.info { "Startet jobb for rapportering av metrikker" }
            }
        midlertidigJournalføringJob
            .start(journalfoeringService)
            .also {
                logger.info { "Startet jobb for midlertidig journalføring" }
            }
        sendBekreftelsesmeldingerJob
            .start()
            .also {
                logger.info { "Startet jobb for å sende bekreftelsesmeldinger" }
            }

        taskExecutor.startExecution()
    }
}
