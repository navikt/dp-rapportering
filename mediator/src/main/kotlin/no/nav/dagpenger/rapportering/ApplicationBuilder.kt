package no.nav.dagpenger.rapportering

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.aktivitetApi
import no.nav.dagpenger.rapportering.api.konfigurasjon
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.repository.InMemoryRepository
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(configuration: Map<String, String>) : RapidsConnection.StatusListener {
    private val rapidsConnection: RapidsConnection =
        RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(configuration))
            .withKtorModule {
                konfigurasjon()
                aktivitetApi(mediator)
                rapporteringApi()
            }.build()
    private val mediator = Mediator(rapidsConnection = rapidsConnection, InMemoryRepository)

    init {
        rapidsConnection.register(this)
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
