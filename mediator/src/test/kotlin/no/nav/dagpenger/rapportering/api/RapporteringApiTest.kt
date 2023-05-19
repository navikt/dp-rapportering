package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID

class RapporteringApiTest {
    @Test
    fun `skal hente en liste med mulige rapportinger`() {
        withRapporteringApi {
            client.get("/rapporteringsperioder").also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne hente ut en rapporteringsperiode med en gitt id`() {
        val id = UUID.randomUUID().toString()
        withRapporteringApi {
            client.get("/rapporteringsperioder/$id").let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne korrigere en periode`() {
        val id = UUID.randomUUID().toString()
        withRapporteringApi {
            client.post("/rapporteringsperioder/$id") {
                contentType(ContentType.Application.Json)
                setBody(
                    //language=JSON
                    """{"start_date": "2023-02-01", "end_date": "2023-02-15"}""",
                )
            }.also { response ->
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
