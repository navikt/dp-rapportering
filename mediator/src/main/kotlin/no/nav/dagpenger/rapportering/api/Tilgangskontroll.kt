package no.nav.dagpenger.rapportering.api

import io.ktor.server.application.ApplicationCall

interface Tilgangskontroll {
    fun verifiserTilgang(call: ApplicationCall): String
}
