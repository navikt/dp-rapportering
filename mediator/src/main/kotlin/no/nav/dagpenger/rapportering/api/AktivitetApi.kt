package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.dagpenger.rapportering.Aktivitet
import no.nav.dagpenger.rapportering.api.dto.DagDTO

fun Application.aktivitetApi() {
    konfigurasjon()
    install(Routing) {
        route("/aktivitet") {
            get {
                val dager = listOf<DagDTO>()
                call.respond(dager)
            }
            post {
                val nyDag = call.receive<NyDag>()
                when (nyDag.aktivitet) {
                    Aktivitet.AktivitetType.Arbeid -> println("arbeid")
                    Aktivitet.AktivitetType.Syk -> println("syk")
                    Aktivitet.AktivitetType.TiltakType -> println("foo")
                    Aktivitet.AktivitetType.FerieType -> println("fbar")
                }

                call.respond(201)
            }
        }
    }
}

private data class NyDag(
    val aktivitet: Aktivitet.AktivitetType,
)
