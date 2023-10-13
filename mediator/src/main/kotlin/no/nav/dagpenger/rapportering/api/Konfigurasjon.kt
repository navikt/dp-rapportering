package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.MethodNotAllowed
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import no.nav.dagpenger.rapportering.GodkjenningExcpetion
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.azureAd
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.tokenX
import no.nav.dagpenger.rapportering.api.models.ProblemDTO
import no.nav.dagpenger.rapportering.serialisering.Jackson.config
import org.slf4j.event.Level
import java.util.UUID

fun Application.konfigurasjon(
    auth: AuthenticationConfig.() -> Unit = {
        jwt("tokenX") { tokenX() }
        jwt("azureAd") { azureAd() }
    },
) {
    install(CallLogging) {
        disableDefaultColors()
        filter {
            it.request.path() !in setOf("/metrics", "/isalive", "/isready")
        }
        level = Level.INFO
    }
    install(ContentNegotiation) {
        jackson {
            config()
        }
    }

    install(Authentication) {
        auth()
    }

    install(StatusPages) {
        exception<IllegalStateException> { call, cause ->
            call.respond(
                MethodNotAllowed,
                ProblemDTO(
                    title = "Ulovlig tilstand",
                    detail = cause.message,
                    status = MethodNotAllowed.value,
                ),
            )
        }
        exception<java.lang.IllegalArgumentException> { call, cause ->
            call.respond(
                BadRequest,
                ProblemDTO(
                    title = "Ulovlig kall",
                    detail = cause.message,
                    status = MethodNotAllowed.value,
                ),
            )
        }

        exception<GodkjenningExcpetion> { call, cause ->
            call.respond(
                Forbidden,
                ProblemDTO(
                    title = "Ulovlig godkjenning",
                    detail = cause.message,
                    status = Forbidden.value,
                ),
            )
        }
    }
}

internal fun ApplicationCall.finnUUID(pathParam: String): UUID =
    parameters[pathParam]?.let {
        UUID.fromString(it)
    } ?: throw IllegalArgumentException("Kunne ikke finne oppgaveId i path")
