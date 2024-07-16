package no.nav.dagpenger.rapportering

import io.ktor.client.engine.cio.CIO
import no.nav.dagpenger.rapportering.config.Configuration
import no.nav.dagpenger.rapportering.connector.createHttpClient

fun main() {
    ApplicationBuilder(Configuration.config, createHttpClient(CIO.create {})).start()
}
