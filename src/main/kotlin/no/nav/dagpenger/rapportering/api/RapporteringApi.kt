package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.repository.RapporteringsRepository

internal fun Application.rapporteringApi(rapporteringsRepository: RapporteringsRepository) {
    routing {
        route("/rapporteringsperioder") {
            get {
                val rapporteringsperioder =
                    rapporteringsRepository
                        .hentRapporteringsperioder("12345678910")

                call.respond(HttpStatusCode.OK, rapporteringsperioder)
            }

            route("/gjeldende") {
                get {
                    val rapporteringsperiode =
                        rapporteringsRepository
                            .hentRapporteringsperioder("12345678910").first()

                    when (rapporteringsperiode) {
                        null -> call.respond(HttpStatusCode.NotFound)
                        else -> call.respond(HttpStatusCode.OK, rapporteringsperiode)
                    }
                }
            }
        }
    }
}
