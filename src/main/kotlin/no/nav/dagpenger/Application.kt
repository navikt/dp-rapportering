package no.nav.dagpenger

import io.ktor.server.application.Application
import no.nav.dagpenger.plugins.configureRouting

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureRouting()
}
