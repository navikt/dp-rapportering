package no.nav.dagpenger.rapportering.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.assertions.json.shouldContainJsonKeyValue
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
import no.nav.dagpenger.rapportering.GodkjenningExcpetion
import no.nav.dagpenger.rapportering.Godkjenningslogg
import no.nav.dagpenger.rapportering.Mediator
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import no.nav.dagpenger.rapportering.api.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.rapportering.api.TestApplication.testAzureAdToken
import no.nav.dagpenger.rapportering.api.TestApplication.testTokenXToken
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringApiTest {
    private val testPeriode =
        Rapporteringsperiode(rapporteringspliktFom = LocalDate.now().minusDays(1)) { _, tom -> tom }
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
    fun `skal hente en liste med alle rapportingsperioder for en gitt ident`() {
        val periodeTilUtfylling =
            Rapporteringsperiode(rapporteringspliktFom = LocalDate.now().minusDays(2)) { _, tom -> tom }
        val korrigert = rapporteringsperiode(Rapporteringsperiode.TilstandType.Innsendt)
        val korrigering =
            rapporteringsperiode(Rapporteringsperiode.TilstandType.TilUtfylling, korrigert) // Skal ikke med
        val korrigertInnsendt = rapporteringsperiode(Rapporteringsperiode.TilstandType.Innsendt)
        val korrigeringInnsendt = rapporteringsperiode(Rapporteringsperiode.TilstandType.Innsendt, korrigertInnsendt)

        withRapporteringApi(
            rapporteringsperioder = listOf(periodeTilUtfylling, korrigert, korrigertInnsendt),
        ) {
            client.get("/rapporteringsperioder") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                response.bodyAsText().let { json ->
                    val data = jacksonObjectMapper().readTree(json)
                    data.size() shouldBe 3
                    data.any { it["id"].asText() == korrigering.rapporteringsperiodeId.toString() } shouldBe false
                    data.any { it["id"].asText() == korrigeringInnsendt.rapporteringsperiodeId.toString() } shouldBe true
                    data.count { it["status"].asText() == "TilUtfylling" } shouldBe 1
                    data.count { it["status"].asText() == "Innsendt" } shouldBe 2
                    json shouldContainJsonKey "$.[0].status"
                }
            }
        }
    }

    @Test
    fun `gjeldende skal respondere med 404 Not Found dersom person ikke har noen rapporteringsperioder`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(),
        ) {
            client.get("/rapporteringsperioder/gjeldende") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.NotFound
                response.bodyAsText() shouldBe ""
            }
        }
    }

    @Test
    fun `gjeldende skal respondere med en periode og 200 OK når person har rapporteringsperiode`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            client.get("/rapporteringsperioder/gjeldende") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                "${response.contentType()}" shouldContain "application/json"
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.status"
                    json.shouldContainJsonKeyValue("$.id", testPeriodeId.toString())
                }
            }
        }
    }

    @Test
    fun `Saksbehandler skal kunne søke etter rapporteringsperioder for en gitt ident`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/sok",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"ident": "$defaultDummyFodselsnummer" }""",
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
    fun `Bruker skal ikke kunne søke etter rapporteringsperioder`() {
        withRapporteringApi(rapporteringsperioder = listOf(testPeriode)) {
            autentisert(
                token = testTokenXToken,
                endepunkt = "/rapporteringsperioder/sok",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"ident": "$defaultDummyFodselsnummer" }""",
            ).let { response ->
                response.status shouldBe HttpStatusCode.Unauthorized
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
    fun `Bruker skal kunne godkjenne og avgodkjenne en rapporteringsperiode uten begrunnelse`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            client.post("/rapporteringsperioder/$testPeriodeId/godkjenn") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                verify {
                    mediatorMock.behandle(any<GodkjennPeriodeHendelse>())
                }
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.id"
                }
            }

            client.post("/rapporteringsperioder/$testPeriodeId/avgodkjenn") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                verify {
                    mediatorMock.behandle(any<AvgodkjennPeriodeHendelse>())
                }
            }
        }
    }

    @Test
    fun `Saksbehandler skal kunne godkjenne og avgodkjenne en rapporteringsperiode med begrunnelse`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/$testPeriodeId/godkjenn",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"begrunnelse": "Begrunnelse" }""",
            ).also { response ->
                response.status shouldBe HttpStatusCode.OK
                verify {
                    mediatorMock.behandle(any<GodkjennPeriodeHendelse>())
                }
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.id"
                }
            }

            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/$testPeriodeId/avgodkjenn",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"begrunnelse": "Begrunnelse" }""",
            ).also { response ->
                response.status shouldBe HttpStatusCode.OK
                verify {
                    mediatorMock.behandle(any<AvgodkjennPeriodeHendelse>())
                }
                response.bodyAsText().let { json ->
                    json shouldContainJsonKey "$.id"
                }
            }
        }
    }

    @Test
    fun `Saksbehandler kan ikke godkjenne eller avgodkjenne en rapporteringsperiode uten en begrunnelse`() {
        withRapporteringApi(
            rapporteringsperioder = listOf(testPeriode),
        ) {
            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/$testPeriodeId/godkjenn",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"begrunnelse": "" }""",
            ).also { response ->
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().let { json ->
                    json.shouldContainJsonKeyValue("$.detail", "Saksbehandler må oppgi begrunnelse")
                }
            }

            autentisert(
                token = testAzureAdToken,
                endepunkt = "/rapporteringsperioder/$testPeriodeId/avgodkjenn",
                httpMethod = HttpMethod.Post,
                //language=JSON
                body = """{"begrunnelse": "" }""",
            ).also { response ->
                response.status shouldBe HttpStatusCode.BadRequest
                response.bodyAsText().let { json ->
                    json.shouldContainJsonKeyValue("$.detail", "Saksbehandler må oppgi begrunnelse")
                }
            }
        }
    }

    @Test
    fun `For tidlig godkjenning gir 405 Method Not Allowed`() {
        val iDag = LocalDate.now()
        val periodeSomIkkeKanGodkjennesEnda = rapporteringsperiode(
            TilUtfylling,
            fom = iDag,
            tom = iDag.plusDays(14),
        )
        val periodeId = periodeSomIkkeKanGodkjennesEnda.rapporteringsperiodeId

        val mediatorMock = mockk<Mediator>().also {
            every { it.behandle(any<GodkjennPeriodeHendelse>()) } throws GodkjenningExcpetion("")
        }

        withRapporteringApi(rapporteringsperioder = listOf(periodeSomIkkeKanGodkjennesEnda), mediatorMock) {
            client.post("/rapporteringsperioder/$periodeId/godkjenn") {
                autentisert()
            }.also { response ->
                response.status shouldBe HttpStatusCode.Forbidden
            }
        }
    }

    @Test
    fun `Skal kunne opprette en korrigering av en rapporteringsperiode`() {
        val korrigert = rapporteringsperiode(Rapporteringsperiode.TilstandType.Innsendt)
        val korrigering = rapporteringsperiode(TilUtfylling, korrigert)

        withRapporteringApi(rapporteringsperioder = listOf(korrigert)) {
            client.post("/rapporteringsperioder/${korrigert.rapporteringsperiodeId}/korrigering") {
                autentisert()
                contentType(ContentType.Application.Json)
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
                verify {
                    mediatorMock.behandle(any<KorrigerPeriodeHendelse>())
                }
                response.bodyAsText().let { json ->
                    json.shouldContainJsonKeyValue("$.id", korrigering.rapporteringsperiodeId.toString())
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
    mock: Mediator = mediatorMock,
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
                mock,
            )
        },
        test = test,
    )
}

private fun rapporteringsperiode(
    tilstandType: Rapporteringsperiode.TilstandType,
    korrigert: Rapporteringsperiode? = null,
    fom: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now(),
) =
    Rapporteringsperiode.rehydrer(
        UUID.randomUUID(),
        beregnesEtter = LocalDate.now(),
        fraOgMed = fom,
        tilOgMed = tom,
        tilstand = tilstandType,
        opprettet = LocalDateTime.now(),
        tidslinje = Aktivitetstidslinje(LocalDate.now()..LocalDate.now()),
        godkjenningslogg = Godkjenningslogg(),
        korrigerer = korrigert,
    )
