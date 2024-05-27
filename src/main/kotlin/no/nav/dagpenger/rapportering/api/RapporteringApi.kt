package no.nav.dagpenger.rapportering.api

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.auth.jwt
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.metrics.RapporteringsperiodeMetrikker
import no.nav.dagpenger.rapportering.model.toResponse
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.rapporteringApi(meldepliktConnector: MeldepliktConnector) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ResponseException -> {
                    logger.error(cause) { "Feil ved uthenting av rapporteringsperiode" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        cause.response.status,
                        HttpProblem(
                            type = URI("urn:connector:rapporteringsperiode"),
                            title = "Feil ved uthenting av rapporteringsperiode",
                            detail = cause.message,
                            status = cause.response.status.value,
                            instance = URI(call.request.uri),
                        ),
                    )
                }

                is JsonConvertException -> {
                    logger.error(cause) { "Feil ved mapping av rapporteringsperiode" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        HttpProblem(title = "Feilet", detail = cause.message),
                    )
                }
                is IllegalArgumentException -> {
                    logger.info(cause) { "Kunne ikke håndtere API kall - Bad request" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400),
                    )
                }

                is NotFoundException -> {
                    logger.info(cause) { "Kunne ikke håndtere API kall - Ikke funnet" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        HttpStatusCode.NotFound,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 404),
                    )
                }

                is BadRequestException -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall - feil i request" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400),
                    )
                }
                else -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall" }
                    MeldepliktMetrikker.meldepliktException.inc()

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        HttpProblem(title = "Feilet", detail = cause.message),
                    )
                }
            }
        }
    }
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperiode") {
                route("/{id}") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        val id = call.parameters["id"]

                        if (id.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }

                        meldepliktConnector
                            .hentRapporteringsperioder(ident, jwtToken)
                            .firstOrNull { it.id.toString() == id }
                            .let {
                                if (it == null) {
                                    call.respond(HttpStatusCode.NotFound)
                                } else {
                                    call.respond(HttpStatusCode.OK, it.toResponse())
                                }
                            }
                    }

                    post {
                        // TODO
                        call.respond(HttpStatusCode.NotImplemented)
                    }

                    route("/korriger") {
                        post {
                            val jwtToken = call.request.jwt()
                            val id = call.parameters["id"]

                            if (id.isNullOrBlank()) {
                                call.respond(HttpStatusCode.BadRequest)
                                return@post
                            }

                            meldepliktConnector
                                .hentKorrigeringId(id, jwtToken)
                                .also { call.respond(HttpStatusCode.OK, it) }
                        }
                    }
                }
            }
            route("/rapporteringsperioder") {
                get {
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()

                    meldepliktConnector
                        .hentRapporteringsperioder(ident, jwtToken)
                        .sortedBy { it.periode.fraOgMed }
                        .also { RapporteringsperiodeMetrikker.hentet.inc() }
                        .also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                }

                route("/gjeldende") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        meldepliktConnector
                            .hentRapporteringsperioder(ident, jwtToken)
                            .minByOrNull { it.periode.fraOgMed }
                            ?.also { call.respond(HttpStatusCode.OK, it) }
                            ?: call.respond(HttpStatusCode.NotFound)
                    }
                }

                route("/innsendte") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        meldepliktConnector
                            .hentSendteRapporteringsperioder(ident, jwtToken)
                            .sortedByDescending { it.periode.fraOgMed }
                            .also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                    }
                }

                // TODO: Fjernes?
                route("/detaljer/{id}") {
                    get {
                        val jwtToken = call.request.jwt()
                        val id = call.parameters["id"]

                        if (id.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }

                        call.respond(HttpStatusCode.OK, meldepliktConnector.hentAktivitetsdager(id, jwtToken))
                    }
                }
            }
        }
    }
}

data class HttpProblem(
    val type: URI = URI.create("about:blank"),
    val title: String,
    val status: Int? = 500,
    val detail: String? = null,
    val instance: URI = URI.create("about:blank"),
    val errorType: String? = null,
)
