package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun Application.internalApi(prometheusMeterRegistry: PrometheusMeterRegistry) {
    routing {
        get("/isalive") {
            call.respondText("Alive")
        }
        get("/isready") {
            call.respondText("Ready")
        }
        get("/metrics") {
            call.respondText(prometheusMeterRegistry.scrape())
        }
    }
}
