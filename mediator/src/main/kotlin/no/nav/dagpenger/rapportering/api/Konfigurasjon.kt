package no.nav.dagpenger.rapportering.api

import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import org.slf4j.event.Level

fun Application.konfigurasjon() {
    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        jackson()
    }
}
