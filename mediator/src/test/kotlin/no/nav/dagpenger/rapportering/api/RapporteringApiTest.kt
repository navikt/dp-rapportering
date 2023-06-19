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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import no.nav.dagpenger.rapportering.api.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.rapportering.api.TestApplication.testAzureAdToken
import no.nav.dagpenger.rapportering.api.TestApplication.testTokenXToken
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RapporteringApiTest {
    private val testPeriode = Rapporteringsperiode(rapporteringspliktFom = LocalDate.now().minusDays(1))
    private val testPeriodeId = testPeriode.rapporteringsperiodeId

    @Test
    fun `uautentiserte GET kall feiler`() {
        withRapporteringApi {
            client.get("/rapporteringsperioder").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `uautentiserte POST kall feiler`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            client.post("/rapporteringsperioder/$testPeriodeId/aktivitet") {
                this.header("Content-Type", "application/json")
                this.setBody("""{"type": "Arbeid", "dato": "2023-05-16", "timer": "7" }""")
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal hente en liste med alle rapportingsperioder`() {
        val periode2 = Rapporteringsperiode(rapporteringspliktFom = LocalDate.now().minusDays(2))
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode, periode2),
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
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            client.get("/rapporteringsperioder/$testPeriodeId") {
                autentisert()
            }.let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.id"
                    json shouldContainJsonKey "$.fraOgMed"
                    json shouldContainJsonKey "$.tilOgMed"
                    json shouldContainJsonKey "$.status"
                    json shouldContainJsonKey "$.dager.[*].dagIndex"
                    json shouldContainJsonKey "$.dager.[*].dato"
                    json shouldContainJsonKey "$.dager.[*].muligeAktiviteter"
                    json shouldContainJsonKey "$.dager.[*].aktiviteter"
                }
            }
        }
    }

    @Test
    fun `Skal kunne godkjenne en rapporteringsperiode`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            client.post("/rapporteringsperioder/$testPeriodeId/godkjenn") {
                autentisert()
                contentType(ContentType.Application.Json)
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
                verify {
                    mediatorMock.behandle(any<GodkjennPeriodeHendelse>())
                }
            }
        }
    }

    @Test
    fun `Skal kunne korrigere en rapporteringsperiode`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            client.post("/rapporteringsperioder/$testPeriodeId/korrigering") {
                autentisert()
                contentType(ContentType.Application.Json)
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                verify {
                    mediatorMock.behandle(any<KorrigerPeriodeHendelse>())
                }
            }
        }
    }

    @Test
    fun `Skal kunne rapportere en aktivitet`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            autentisert(
                endepunkt = "/rapporteringsperioder/$testPeriodeId/aktivitet",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"type": "Arbeid", "dato": "2023-05-16", "timer": "PT7H" }""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
                verify {
                    mediatorMock.behandle(any<NyAktivitetHendelse>())
                }
            }
        }
    }

    @Test
    fun `Skal som saksbehandler kunne søke etter rapporteringsperioder`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/sok",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"ident": "12345" }""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "[*].id"
                }
            }
        }
    }

    @Test
    fun `Skal som bruker ikke kunne søke etter rapporteringsperioder`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            autentisert(
                token = testTokenXToken,
                endepunkt = "/rapporteringsperioder/sok",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"ident": "12345" }""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    @Test
    fun `Skal kunne slette en aktivitet`() {
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 4)
        withRapporteringApi {
            autentisert(
                "/rapporteringsperioder/$testPeriodeId/aktivitet/${aktivitet.uuid}",
                httpMethod = HttpMethod.Delete,
            ).let { response ->
                response.status shouldBe HttpStatusCode.NoContent
                verify {
                    mediatorMock.behandle(any<SlettAktivitetHendelse>())
                }
            }
        }
    }
}

private val mediatorMock = mockk<Mediator>(relaxed = true)

private fun withRapporteringApi(
    rapporteringsperioder: List<Rapporteringsperiode> = emptyList(),
    test: suspend ApplicationTestBuilder.() -> Unit,
) {
    TestApplication.withMockAuthServerAndTestApplication(
        moduleFunction = {
            konfigurasjon()
            rapporteringApi(
                mockk<RapporteringsperiodeRepository>().apply {
                    every { finnIdentForPeriode(any()) } answers { defaultDummyFodselsnummer }
                    every { hentRapporteringsperioder(defaultDummyFodselsnummer) } answers { rapporteringsperioder }
                    every {
                        hentRapporteringsperiode(
                            defaultDummyFodselsnummer,
                            any(),
                        )
                    } answers { rapporteringsperioder.single() }
                },
                mediatorMock,
            )
        },
        test = test,
    )
}
