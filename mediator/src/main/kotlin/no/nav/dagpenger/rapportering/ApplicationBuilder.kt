package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.konfigurasjon
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.jobs.InnsendingsJobb
import no.nav.dagpenger.rapportering.jobs.NyRapporteringssyklusJobb
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.dagpenger.rapportering.tjenester.RapporteringJournalførtMottak
import no.nav.dagpenger.rapportering.tjenester.RapporteringMellomlagretMottak
import no.nav.dagpenger.rapportering.tjenester.RapporteringspliktDatoMottak
import no.nav.dagpenger.rapportering.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.tjenester.VedtakMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(configuration: Map<String, String>) : RapidsConnection.StatusListener {
    private val postgresRepository = PostgresRepository(dataSource)
    private val rapidsConnection: RapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(configuration))
            .withKtorModule {
                konfigurasjon()
                rapporteringApi(postgresRepository, mediator)
            }.build()
    private val mediator = Mediator(
        rapidsConnection = rapidsConnection,
        postgresRepository,
        BehovMediator(rapidsConnection),
        AktivitetsloggMediator(rapidsConnection),
    )

    init {
        rapidsConnection.register(this)
        SøknadMottak(rapidsConnection, mediator)
        RapporteringspliktDatoMottak(rapidsConnection, mediator)
        VedtakMottak(rapidsConnection, mediator)
        RapporteringMellomlagretMottak(rapidsConnection, mediator)
        RapporteringJournalførtMottak(rapidsConnection, mediator)
    }

    fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
        logger.info { "Starter appen ${Configuration.appName}" }

        InnsendingsJobb.start(mediator)
        NyRapporteringssyklusJobb.start(mediator)
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        logger.info { "Skrur av applikasjonen" }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}
