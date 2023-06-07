package no.nav.dagpenger.rapportering.api

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.tokenX
import no.nav.dagpenger.rapportering.serialisering.Jackson.config
import org.slf4j.event.Level
import java.util.UUID

fun Application.konfigurasjon() {
    install(CallLogging) {
        disableDefaultColors()
        filter {
            !it.request.path().startsWith("/internal")
        }
        level = Level.INFO
    }
    install(ContentNegotiation) {
        jackson {
            config()
        }
    }

    install(Authentication) {
        jwt("tokenX") {
            tokenX()
        }
    }
}

internal fun ApplicationCall.finnUUID(pathParam: String): UUID = parameters[pathParam]?.let {
    UUID.fromString(it)
} ?: throw IllegalArgumentException("Kunne ikke finne oppgaveId i path")
