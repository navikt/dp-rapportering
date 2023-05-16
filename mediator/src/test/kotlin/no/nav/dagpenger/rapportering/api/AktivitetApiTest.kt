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

class AktivitetApiTest {
    @Test
    fun `Skal kunne lage en aktivitet`() {
        withAktivitetApi {
            client.post("/aktivitet") {
                contentType(ContentType.Application.Json)
                setBody(
                    //language=JSON
                    """{"type": "Arbeid", "dato": "2023-05-16", "timer": "7.5" }""",
                )
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    private fun withAktivitetApi(
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        testApplication {
            application {
                konfigurasjon()
                aktivitetApi()
            }
            test()
        }
    }
}
