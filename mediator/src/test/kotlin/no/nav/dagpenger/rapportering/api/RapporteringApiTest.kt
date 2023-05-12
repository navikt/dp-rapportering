package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class RapporteringApiTest {
    @Test
    fun `skal hente en liste med mulige rapportinger`() {
        withRapporteringApi {
            client.get("/rapportering").also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    private fun withRapporteringApi(
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        testApplication {
            application {
                konfigurasjon()
                rapporteringApi()
            }
            test()
        }
    }
}
