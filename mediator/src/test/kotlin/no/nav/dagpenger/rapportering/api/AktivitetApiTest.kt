package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import java.util.UUID

class AktivitetApiTest {

    @Test
    fun `Skal kunne hente ut alle aktiviteter`() {
        withAktivitetApi {
            client.get("/aktivitet").let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne lage en aktivitet`() {
        withAktivitetApi {
            client.post("/aktivitet") {
                contentType(ContentType.Application.Json)
                setBody(
                    //language=JSON
                    """{"type": "work", "dato": "2023-05-16", "timer": "7" }""",
                )
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne hente ut en aktvitet med en gitt id`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            client.get("/aktivitet/$id").let { response ->
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Skal kunne gjøre endringer på en eksisterende aktivitet`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            client.put("/aktivitet/$id") {
                contentType(ContentType.Application.Json)
                setBody(
                    //language=JSON
                    """{"type": "work", "dato": "2023-05-16", "timer": "5"}""",
                )
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    @Test
    fun `Skal kunne slette en aktivitet`() {
        val id = UUID.randomUUID().toString()
        withAktivitetApi {
            client.delete("/aktivitet/$id").let { response ->
                response.status shouldBe HttpStatusCode.NoContent
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
