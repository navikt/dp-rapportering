package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.mockk
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import org.junit.jupiter.api.Test
import java.util.UUID

class AktivitetApiTest {

    @Test
    fun `uautentiserte kall feiler`() {
        withAktivitetApi {
            client.get("/aktivitet").let { response ->
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Skal kunne hente ut alle aktiviteter`() {
        withAktivitetApi {
            autentisert(
                httpMethod = HttpMethod.Get,
                endepunkt = "/aktivitet",
            ).let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne lage en aktivitet`() {
        withAktivitetApi {
            autentisert(
                endepunkt = "/aktivitet",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"type": "Arbeid", "dato": "2023-05-16", "timer": "7" }""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne hente ut en aktvitet med en gitt id`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            autentisert(
                "/aktivitet/$id",
                httpMethod = HttpMethod.Get,
            ).status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `Skal kunne gjøre endringer på en eksisterende aktivitet`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            autentisert(
                endepunkt = "/aktivitet/$id",
                httpMethod = HttpMethod.Put,
                //language=JSON
                body = """{"type": "Arbeid", "dato": "2023-05-16", "timer": "5"}""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Skal kunne slette en aktivitet`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            autentisert("/aktivitet/$id", httpMethod = HttpMethod.Delete).let { response ->
                response.status shouldBe HttpStatusCode.NoContent
            }
        }
    }

    private fun withAktivitetApi(
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        TestApplication.withMockAuthServerAndTestApplication(
            moduleFunction = {
                konfigurasjon()
                aktivitetApi(mockk(relaxed = true))
            },
            test = test,
        )
    }
}
