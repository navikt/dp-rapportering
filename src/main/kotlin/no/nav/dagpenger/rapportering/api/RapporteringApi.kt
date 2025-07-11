package no.nav.dagpenger.rapportering.api

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.auth.jwt
import no.nav.dagpenger.rapportering.api.auth.loginLevel
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.erEndring
import no.nav.dagpenger.rapportering.model.toResponse
import no.nav.dagpenger.rapportering.service.PersonregisterService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.utils.getCallId
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun Application.rapporteringApi(
    rapporteringService: RapporteringService,
    personregisterService: PersonregisterService,
    meldepliktMetrikker: MeldepliktMetrikker,
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ResponseException -> {
                    logger.error(cause) { "Feil ved uthenting av rapporteringsperiode" }
                    meldepliktMetrikker.rapporteringApiFeil.increment()

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
                    meldepliktMetrikker.rapporteringApiFeil.increment()

                    call.respond(
                        HttpStatusCode.InternalServerError,
                        HttpProblem(title = "Feilet", detail = cause.message),
                    )
                }

                is IllegalArgumentException -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall - Bad request" }
                    meldepliktMetrikker.rapporteringApiFeil.increment()

                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400),
                    )
                }

                is NotFoundException -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall - Ikke funnet" }
                    meldepliktMetrikker.rapporteringApiFeil.increment()

                    call.respond(
                        HttpStatusCode.NotFound,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 404),
                    )
                }

                is BadRequestException -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall - Feil i request" }
                    meldepliktMetrikker.rapporteringApiFeil.increment()

                    call.respond(
                        HttpStatusCode.BadRequest,
                        HttpProblem(title = "Feilet", detail = cause.message, status = 400),
                    )
                }

                else -> {
                    logger.error(cause) { "Kunne ikke håndtere API kall" }
                    meldepliktMetrikker.rapporteringApiFeil.increment()

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
            route("/hardpmeldeplikt") {
                get {
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()

                    rapporteringService
                        .harDpMeldeplikt(ident, jwtToken)
                        .also { call.respond(HttpStatusCode.OK, it) }
                }
            }
            route("/rapporteringsperiode") {
                post {
                    val ident = call.ident()
                    val loginLevel = call.loginLevel()
                    val jwtToken = call.request.jwt()
                    val headers = call.request.headers

                    val rapporteringsperiode = call.receive(Rapporteringsperiode::class)

                    logger.info { "Rapporteringsperiode: $rapporteringsperiode" }

                    try {
                        val response =
                            rapporteringService.sendRapporteringsperiode(
                                rapporteringsperiode,
                                jwtToken,
                                ident,
                                loginLevel,
                                headers,
                            )

                        if (response.status == "OK") {
                            meldepliktMetrikker.innsendteMeldekortOk.increment()
                            if (rapporteringsperiode.dager.all { dag -> dag.aktiviteter.isEmpty() }) {
                                meldepliktMetrikker.innsendteIngenAktiviteter.increment()
                            }
                            if (rapporteringsperiode.erEndring()) {
                                meldepliktMetrikker.innsendteEndring.increment()
                            }
                            call.respond(HttpStatusCode.OK, response)
                        } else {
                            meldepliktMetrikker.innsendteMeldekortIkkeOk.increment()
                            call.respond(HttpStatusCode.BadRequest, response)
                        }
                    } catch (e: Exception) {
                        meldepliktMetrikker.innsendteMeldekortFeil.increment()
                        logger.error("Feil ved innsending: $e")
                        throw e
                    }
                }

                route("/{id}") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        val rapporteringId = call.getParameter("id")
                        val hentOriginal = call.request.header("Hent-Original")?.toBoolean() ?: true

                        rapporteringService
                            .hentPeriode(rapporteringId, ident, jwtToken, hentOriginal)
                            ?.also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                            ?: throw NotFoundException(
                                "Rapportering med id $rapporteringId ikke funnet. Header Hent-Original: $hentOriginal",
                            )
                    }

                    route("/start") {
                        post {
                            val ident = call.ident()
                            val jwtToken = call.request.jwt()
                            val rapporteringId = call.getParameter("id")

                            rapporteringService
                                .startUtfylling(rapporteringId, ident, jwtToken)
                                .also { call.respond(HttpStatusCode.OK) }
                        }
                    }

                    route("/arbeidssoker") {
                        post {
                            val ident = call.ident()
                            val rapporteringId = call.getParameter("id")
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
                            val rapporteringId = call.getParameter("id")
                            val dag = call.receive(Dag::class)

                            rapporteringService.lagreEllerOppdaterAktiviteter(rapporteringId, dag)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/aktiviteter") {
                        delete {
                            val ident = call.ident()
                            val rapporteringId = call.getParameter("id")

                            rapporteringService.resettAktiviteter(rapporteringId, ident)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/begrunnelse") {
                        post {
                            val ident = call.ident()
                            val rapporteringId = call.getParameter("id")
                            val begrunnelse = call.receive(BegrunnelseRequest::class)

                            rapporteringService.oppdaterBegrunnelse(rapporteringId, ident, begrunnelse.begrunnelseEndring)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/rapporteringstype") {
                        post {
                            val ident = call.ident()
                            val rapporteringId = call.getParameter("id")
                            val rapporteringstype = call.receive(RapporteringstypeRequest::class).rapporteringstype

                            if (rapporteringstype.isBlank()) {
                                throw BadRequestException("Rapporteringstype kan ikke være tom")
                            }

                            rapporteringService.oppdaterRapporteringstype(rapporteringId, ident, rapporteringstype)
                            call.respond(HttpStatusCode.NoContent)
                        }
                    }

                    route("/endre") {
                        post {
                            val ident = call.ident()
                            val jwtToken = call.request.jwt()
                            val id = call.getParameter("id")

                            rapporteringService
                                .startEndring(id, ident, jwtToken)
                                .also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                        }
                    }
                }
            }
            route("/rapporteringsperioder") {
                get {
                    val ident = call.ident()
                    val jwtToken = call.request.jwt()

                    personregisterService.oppdaterPersonstatus(ident, jwtToken)

                    rapporteringService
                        .hentOgOppdaterRapporteringsperioder(ident, jwtToken)
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
                            ?.also { call.respond(HttpStatusCode.OK, defaultObjectMapper.writeValueAsString(it.toResponse())) }
                            ?: call.respond(HttpStatusCode.NoContent)
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
    val correlationId: String = getCallId(),
)

data class ArbeidssokerRequest(
    val registrertArbeidssoker: Boolean,
)

data class BegrunnelseRequest(
    val begrunnelseEndring: String,
)

data class RapporteringstypeRequest(
    val rapporteringstype: String,
)

private fun ApplicationCall.getParameter(name: String): String =
    this.parameters[name] ?: throw BadRequestException("Parameter $name not found")
