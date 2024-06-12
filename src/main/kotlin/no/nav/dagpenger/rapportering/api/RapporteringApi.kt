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
import no.nav.dagpenger.rapportering.api.models.AktivitetResponse
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeResponse
import no.nav.dagpenger.rapportering.api.models.DagInnerResponse
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.toResponse
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.RapporteringService
import java.net.URI
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal fun Application.rapporteringApi(
    meldepliktConnector: MeldepliktConnector,
    rapporteringService: RapporteringService,
    journalfoeringService: JournalfoeringService,
) {
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

                    try {
                        // Send data
                        val response = meldepliktConnector.sendinnRapporteringsperiode(rapporteringsperiode, jwtToken)

                        // Journalfør hvis status er OK
                        if (response.status == "OK") {
                            logger.info("Journalføring rapporteringsperiode ${rapporteringsperiode.id}")
                            journalfoeringService.journalfoer(ident, loginLevel, rapporteringsperiode)
                        }

                        call.respond(response)
                    } catch (e: Exception) {
                        logger.error("Feil ved innsending: $e")
                        call.respond(HttpStatusCode.InternalServerError)
                    }
                }

                route("/gjeldende") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()

                        rapporteringService.hentGjeldendePeriode(ident, jwtToken)
                            ?.also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                            ?: call.respond(HttpStatusCode.NotFound)
                    }
                }

                route("/{id}") {
                    get {
                        val ident = call.ident()
                        val jwtToken = call.request.jwt()
                        val rapporteringId = call.parameters["id"]

                        if (rapporteringId.isNullOrBlank()) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@get
                        }

                        rapporteringService
                            .hentPeriode(rapporteringId.toLong(), ident, jwtToken)
                            ?.also { call.respond(HttpStatusCode.OK, it.toResponse()) }
                            ?: call.respond(HttpStatusCode.NotFound)
                    }

                    route("/aktivitet") {
                        post {
                            val rapporteringId = call.parameters["id"]?.toLong()
                            val dag = call.receive(DagInnerResponse::class).toDag()

                            if (rapporteringId == null) {
                                call.respond(HttpStatusCode.BadRequest)
                            } else {
                                rapporteringService.lagreAktiviteter(rapporteringId, dag)
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                        route("/{aktivitetId}") {
                            delete {
                                val aktivitetId = call.parameters["aktivitetId"]

                                if (aktivitetId == null) {
                                    call.respond(HttpStatusCode.BadRequest)
                                }

                                rapporteringService.slettAktivitet(UUID.fromString(aktivitetId))
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                    }

                    route("/korriger") {
                        post {
                            val jwtToken = call.request.jwt()
                            val id = call.parameters["id"]

                            if (id.isNullOrBlank()) {
                                call.respond(HttpStatusCode.BadRequest)
                                return@post
                            }

                            rapporteringService
                                .korrigerMeldekort(id.toLong(), jwtToken)
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
                        .hentAlleRapporteringsperioder(ident, jwtToken)
                        .also {
                            if (it == null) {
                                call.respond(HttpStatusCode.NoContent)
                            } else {
                                call.respond(HttpStatusCode.OK, it.toResponse())
                            }
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

private fun DagInnerResponse.toDag() =
    Dag(
        dato = dato,
        aktiviteter = aktiviteter.map { it.toAktivitet() },
        dagIndex = dagIndex.toInt(),
    )

private fun AktivitetResponse.toAktivitet() =
    Aktivitet(
        uuid = id,
        type =
            when (type) {
                AktivitetTypeResponse.Arbeid -> AktivitetsType.Arbeid
                AktivitetTypeResponse.Syk -> AktivitetsType.Syk
                AktivitetTypeResponse.Utdanning -> AktivitetsType.Utdanning
                AktivitetTypeResponse.Fravaer -> AktivitetsType.FerieEllerFravaer
            },
        timer = timer,
    )
