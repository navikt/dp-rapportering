package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.models.PeriodeKorreksjonsInput
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode

fun Application.rapporteringApi() {
    routing {
        route("/rapporteringsperioder") {
            get {
                val rapporteringsperioder = listOf<Rapporteringsperiode>()
                call.respond(HttpStatusCode.OK, rapporteringsperioder)
            }

            route("{id}") {
                get {
                    val rapporteringsperiode = Rapporteringsperiode()
                    call.respond(HttpStatusCode.OK, rapporteringsperiode)
                }

                post {
                    val korrigering = call.receive<PeriodeKorreksjonsInput>()
                    call.respond(
                        HttpStatusCode.OK,
                        Rapporteringsperiode(
                            startDate = korrigering.startDate,
                            endDate = korrigering.endDate,
                        ),
                    )
                }
            }
        }
    }
}
