package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

fun Application.internalApi() {
    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    routing {
        get("/isAlive") {
            call.respondText("Alive")
        }
        get("/isReady") {
            call.respondText("Ready")
        }
        get("/metrics") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
