package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.konfigurasjon
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.repository.InMemoryAktivitetRepository
import no.nav.dagpenger.rapportering.repository.InMemoryPersonRepository
import no.nav.dagpenger.rapportering.repository.InMemoryRapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tjenester.SøknadMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(configuration: Map<String, String>) : RapidsConnection.StatusListener {
    private val aktivitetRepository = InMemoryAktivitetRepository()
    private val rapporteringsperiodeRepository = InMemoryRapporteringsperiodeRepository()
    private val rapidsConnection: RapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(configuration))
            .withKtorModule {
                konfigurasjon()
                rapporteringApi(rapporteringsperiodeRepository, mediator)
            }.build()
    private val mediator = Mediator(
        rapidsConnection = rapidsConnection,
        InMemoryPersonRepository(rapporteringsperiodeRepository),
    )

    init {
        rapidsConnection.register(this)
        SøknadMottak(rapidsConnection, mediator)
    }

    fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        logger.info { "Starter appen ${Configuration.appName}" }
    }

    override fun onShutdown(rapidsConnection: RapidsConnection) {
        logger.info { "Skrur av applikasjonen" }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}
