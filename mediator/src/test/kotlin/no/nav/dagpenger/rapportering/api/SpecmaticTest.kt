package no.nav.dagpenger.rapportering.api

import `in`.specmatic.test.SpecmaticJUnitSupport
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import no.nav.helse.rapids_rivers.KtorBuilder
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled

@Disabled("Veldig mye mangler i APIet enda")
object SpecmaticTest : SpecmaticJUnitSupport() {
    private lateinit var server: ApplicationEngine

    @BeforeAll
    @JvmStatic
    fun setUp() {
        System.setProperty("host", "localhost")
        System.setProperty("port", "8081")
        System.setProperty("endpointsAPI", "http://0.0.0.0:8081/")

        System.setProperty("SPECMATIC_GENERATIVE_TESTS", "true")
        server = KtorBuilder().port(8081).module {
            konfigurasjon()
            aktivitetApi()
        }.build(CIO).start()
    }

    @AfterAll
    @JvmStatic
    fun tearDown() {
        server.stop()
    }
}
