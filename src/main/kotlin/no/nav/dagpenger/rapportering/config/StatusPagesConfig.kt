package no.nav.dagpenger.rapportering.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import no.nav.dagpenger.rapportering.api.HttpProblem
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import java.net.URI

private val logger = KotlinLogging.logger {}

internal fun StatusPagesConfig.statusPagesConfig(meldepliktMetrikker: MeldepliktMetrikker) {
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
