package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.auth.jwt
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.metrics.RapporteringsperiodeMetrikker

internal fun Application.rapporteringApi(meldepliktConnector: MeldepliktConnector) {
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()

                    meldepliktConnector
                        .hentMeldekort(ident, jwtToken)
                        .also { RapporteringsperiodeMetrikker.hentet.inc() }
                        .also { call.respond(HttpStatusCode.OK, it) }
                }

                route("/gjeldende") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        meldepliktConnector
                            .hentMeldekort(ident, jwtToken)
                            .firstOrNull()
                            ?.also { call.respond(HttpStatusCode.OK, it) }
                            ?: call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
