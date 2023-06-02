package no.nav.dagpenger.rapportering.api

import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import no.nav.dagpenger.rapportering.api.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.rapportering.repository.InMemoryAktivitetRepository
import no.nav.dagpenger.rapportering.repository.InMemoryRapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RapporteringApiTest {

    @Test
    fun `uautentiserte get kall feiler`() {
        withRapporteringApi {
            client.get("/rapporteringsperioder").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Disabled
    @Test
    fun `uautentiserte POST kall feiler`() {
        withRapporteringApi() {
            client.post("/rapporteringsperioder/aktivitet") {
                this.header("Content-Type", "application/json")
                this.setBody("""{"type": "Arbeid", "dato": "2023-05-16", "timer": "7" }""")
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal hente en liste med mulige rapportinger`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        withRapporteringApi(
            rapporteringsperioder = listOf(periode1),
        ) {
            client.get("/rapporteringsperioder") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.[0].status"
                }
            }
        }
    }

    @Test
    fun `Skal kunne hente ut en rapporteringsperiode med en gitt id`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId
        withRapporteringApi(
            rapporteringsperioder = listOf(periode1),
        ) {
            client.get("/rapporteringsperioder/$periodeId") {
                autentisert()
            }.let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                // todo sjekke json
            }
        }
    }

    @Test
    fun `Skal kunne ferdigstille en rapporteringsperiode`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId
        withRapporteringApi(
            rapporteringsperioder = listOf(periode1),
        ) {
            client.post("/rapporteringsperioder/$periodeId/godkjenn") {
                autentisert()
                contentType(ContentType.Application.Json)
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne hente ut alle aktiviteter`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId

        withRapporteringApi(rapporteringsperioder = listOf(periode1)) {
            autentisert(
                httpMethod = HttpMethod.Get,
                endepunkt = "/rapporteringsperioder/$periodeId/aktivitet",
            ).let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    @Test
    fun `Skal kunne rapportere en aktivitet`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId
        withRapporteringApi(rapporteringsperioder = listOf(periode1)) {
            autentisert(
                endepunkt = "/rapporteringsperioder/$periodeId/aktivitet",
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
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 4)
        withRapporteringApi(rapporteringsperioder = listOf(periode1), aktiviteter = listOf(aktivitet)) {
            autentisert(
                "/rapporteringsperioder/$periodeId/aktivitet/${aktivitet.uuid}",
                httpMethod = HttpMethod.Get,
            ).status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `Skal kunne slette en aktivitet`() {
        val periode1 = Rapporteringsperiode(
            rapporteringspliktFom = LocalDate.now().minusDays(1),
        )
        val periodeId = periode1.rapporteringsperiodeId
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 4)
        withRapporteringApi {
            autentisert("/rapporteringsperioder/$periodeId/aktivitet/${aktivitet.uuid}", httpMethod = HttpMethod.Delete).let { response ->
                response.status shouldBe HttpStatusCode.NoContent
            }
        }
    }
}

private fun withRapporteringApi(
    rapporteringsperioder: List<Rapporteringsperiode> = emptyList(),
    aktiviteter: List<Aktivitet> = emptyList(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    TestApplication.withMockAuthServerAndTestApplication(
        moduleFunction = {
            konfigurasjon()
            rapporteringApi(
                InMemoryRapporteringsperiodeRepository().apply {
                    rapporteringsperioder.forEach {
                        lagreRapporteringsperiode(defaultDummyFodselsnummer, it)
                    }
                },
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
