package no.nav.dagpenger.rapportering

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.api.internalApi
import no.nav.dagpenger.rapportering.api.konfigurasjon
import no.nav.dagpenger.rapportering.api.rapporteringApi
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    konfigurasjon(appMicrometerRegistry)
    internalApi(appMicrometerRegistry)
    rapporteringApi(MeldepliktConnector())
}
