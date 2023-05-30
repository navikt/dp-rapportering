package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import no.nav.dagpenger.rapportering.api.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.rapportering.repository.InMemoryAktivitetRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class AktivitetApiTest {
    @Test
    fun `uautentiserte get kall feiler`() {
        withAktivitetApi {
            client.get("/aktivitet").status shouldBe HttpStatusCode.Unauthorized
            client.get("/aktivitet/id").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `uautentiserte PUT eller POST kall feiler`() {
        withAktivitetApi {
            listOf(HttpMethod.Post, HttpMethod.Get).forEach { httpMethod ->
                client.request("/aktivitet") {
                    this.method = httpMethod
                    this.header("Content-Type", "application/json")
                    this.setBody("""{"type": "Arbeid", "dato": "2023-05-16", "timer": "7" }""")
                }.status shouldBe HttpStatusCode.Unauthorized
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
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 4)
        withAktivitetApi(listOf(aktivitet)) {
            autentisert(
                "/aktivitet/${aktivitet.uuid}",
                httpMethod = HttpMethod.Get,
            ).status shouldBe HttpStatusCode.OK
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
        aktiviteter: List<Aktivitet> = emptyList(),
        test: suspend ApplicationTestBuilder.() -> Unit,
    ) {
        TestApplication.withMockAuthServerAndTestApplication(
            moduleFunction = {
                konfigurasjon()
                aktivitetApi(
                    InMemoryAktivitetRepository(
                        mutableMapOf<String, MutableList<Aktivitet>>().apply {
                            put(defaultDummyFodselsnummer, aktiviteter.toMutableList())
                        },
                    ),
                )
            },
            test = test,
        )
    }
}
