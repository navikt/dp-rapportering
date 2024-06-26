package no.nav.dagpenger.rapportering.api

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.auth.jwt
import no.nav.dagpenger.rapportering.api.auth.loginLevel
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.toResponse
import no.nav.dagpenger.rapportering.service.RapporteringService
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.rapporteringApi(rapporteringService: RapporteringService) {
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
                post {
                    val ident = call.ident()
                    val loginLevel = call.loginLevel()
                    val jwtToken = call.request.jwt()

                    val rapporteringsperiode = call.receive(Rapporteringsperiode::class)

                    logger.info { "Rapporteringsperiode: $rapporteringsperiode" }

                    try {
                        rapporteringService.sendRapporteringsperiode(
                            rapporteringsperiode,
                            jwtToken,
                            ident,
                            loginLevel,
                        )
                        call.respond(HttpStatusCode.OK)
                    } catch (e: Exception) {
                        logger.error("Feil ved innsending: $e")
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                route("/{id}") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        val rapporteringId = call.getParameter("id").toLong()

                        rapporteringService
                            .hentPeriode(rapporteringId, ident, jwtToken)
                            ?.also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                            ?: call.respond(HttpStatusCode.NotFound)
                    }

                    route("/start") {
                        post {
                            val ident = call.ident()
                            val jwtToken = call.request.jwt()
                            val rapporteringId = call.getParameter("id").toLong()

                            rapporteringService
                                .startUtfylling(rapporteringId, ident, jwtToken)
                                .also { call.respond(HttpStatusCode.OK) }
                        }
                    }

                    route("/arbeidssoker") {
                        post {
                            val ident = call.ident()
                            val rapporteringId = call.getParameter("id").toLong()
                            val arbeidssokerRequest = call.receive(ArbeidssokerRequest::class)

                            rapporteringService.oppdaterRegistrertArbeidssoker(
                                rapporteringId,
                                ident,
                                arbeidssokerRequest.registrertArbeidssoker,
                            )
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/aktivitet") {
                        post {
                            val rapporteringId = call.getParameter("id").toLong()
                            val dag = call.receive(Dag::class)

                            rapporteringService.lagreEllerOppdaterAktiviteter(rapporteringId, dag)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/korriger") {
                        post {
                            val ident = call.ident()
                            val jwtToken = call.request.jwt()
                            val id = call.getParameter("id")

                            rapporteringService
                                .korrigerRapporteringsperiode(id.toLong(), ident, jwtToken)
                                .also { call.respond(HttpStatusCode.OK, it) }
                        }
                    }
                }
            }
            route("/rapporteringsperioder") {
                get {
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()

                    rapporteringService
                        .hentAllePerioderSomKanSendes(ident, jwtToken)
                        .also {
                            if (it == null) {
                                call.respond(HttpStatusCode.NoContent)
                                return@get
                            }
                            call.respond(HttpStatusCode.OK, it.toResponse())
                        }
                }

                route("/innsendte") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()

                        rapporteringService
                            .hentInnsendteRapporteringsperioder(ident, jwtToken)
                            .also { call.respond(HttpStatusCode.OK, it.toResponse()) }
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

data class ArbeidssokerRequest(
    val registrertArbeidssoker: Boolean,
)

private fun ApplicationCall.getParameter(name: String): String =
    this.parameters[name] ?: throw BadRequestException("Parameter $name not found")
