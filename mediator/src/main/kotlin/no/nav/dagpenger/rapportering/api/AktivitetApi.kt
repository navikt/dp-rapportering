package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.Aktivitet
import no.nav.dagpenger.rapportering.api.dto.DagDTO

fun Application.aktivitetApi() {
    routing {
        route("/aktivitet") {
            get {
                val dager = listOf<DagDTO>()
                call.respond(dager)
            }
            post {
                val nyDag = call.receive<NyDag>()
                call.respond(201)
            }
        }
    }
}

private data class NyDag(
    val aktivitet: Aktivitet.AktivitetType,
)
