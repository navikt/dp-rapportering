package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode
import java.util.UUID

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

                route("/innsending") {
                    post {
                        val id = call.parameters["id"]?.let {
                            UUID.fromString(it)
                        }

                        call.respond(
                            HttpStatusCode.Created,
                            Rapporteringsperiode(id = id),
                        )
                    }
                }
            }
        }
    }
}
