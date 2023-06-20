package no.nav.dagpenger.rapportering.api

import `in`.specmatic.test.SpecmaticJUnitSupport
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.helse.rapids_rivers.KtorBuilder
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled

@Disabled("Veldig mye mangler i APIet enda")
object SpecmaticTest : SpecmaticJUnitSupport() {
    private lateinit var server: ApplicationEngine

    @BeforeAll
    @JvmStatic
    fun setUp() {
        withMigratedDb {
            System.setProperty("host", "localhost")
            System.setProperty("port", "8081")
            System.setProperty("endpointsAPI", "http://0.0.0.0:8081/")

            System.setProperty("SPECMATIC_GENERATIVE_TESTS", "true")

            val rapporteringsperiodeRepository = PostgresRepository(PostgresDataSourceBuilder.dataSource)
            server = KtorBuilder().port(8081).module {
                konfigurasjon {
                    bearer("tokenX") {
                        authenticate {
                            UserIdPrincipal("jetbrains")
                        }
                    }
                    bearer("azureAd") {
                        authenticate {
                            UserIdPrincipal("jetbrains")
                        }
                    }
                }
                rapporteringApi(rapporteringsperiodeRepository, Mediator(TestRapid(), rapporteringsperiodeRepository))
            }.build(CIO).start()
        }
    }

    @AfterAll
    @JvmStatic
    fun tearDown() {
        server.stop()
    }
}
