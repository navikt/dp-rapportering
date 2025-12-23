package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.utils.Sikkerlogg

fun Application.internalApi(prometheusMeterRegistry: PrometheusMeterRegistry) {
    routing {
        get("/isalive") {
            Sikkerlogg.info { "Alive" }
            call.respondText("Alive")
        }
        get("/isready") {
            Sikkerlogg.info { "Ready" }
            call.respondText("Ready")
        }
        get("/metrics") {
            call.respondText(prometheusMeterRegistry.scrape())
        }
    }
}
