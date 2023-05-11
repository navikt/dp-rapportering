package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.dagpenger.rapportering.api.dto.DagDTO

fun Application.rapporteringApi() {
    install(Routing) {
        route("/rapportering") {
            get {
                val dager = listOf<DagDTO>()
                call.respond(dager)
            }
        }
    }
}

