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
    fun `skal hente en liste med mulige rapportinger`() {
        withAktivitetApi {
            client.post("/aktivitet") {
                contentType(ContentType.Application.Json)
                setBody(
                    //language=JSON
                    """{"aktivitet": "Arbeid"}""",
                )
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    private fun withAktivitetApi(
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        testApplication {
            application {
                aktivitetApi()
            }
            test()
        }
    }
}
