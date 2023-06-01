package no.nav.dagpenger.rapportering.api

import io.kotest.assertions.json.shouldContainJsonKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.api.TestApplication.autentisert
import no.nav.dagpenger.rapportering.api.TestApplication.defaultDummyFodselsnummer
import no.nav.dagpenger.rapportering.repository.InMemoryAktivitetRepository
import no.nav.dagpenger.rapportering.repository.InMemoryRapporteringsperiodeRepository
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RapporteringApiTest {
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
        val id = periode1.rapporteringsperiodeId
        withRapporteringApi(
            rapporteringsperioder = listOf(periode1),
        ) {
            client.get("/rapporteringsperioder/$id") {
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
        val id = periode1.rapporteringsperiodeId
        withRapporteringApi(
            rapporteringsperioder = listOf(periode1),
        ) {
            client.post("/rapporteringsperioder/$id/godkjenn") {
                autentisert()
                contentType(ContentType.Application.Json)
            }.also { response ->
                response.status shouldBe HttpStatusCode.Created
                "${response.contentType()}" shouldContain "application/json"
            }
        }
    }

    private fun withRapporteringApi(
        rapporteringsperioder: List<Rapporteringsperiode> = emptyList(),
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
                    InMemoryAktivitetRepository(),
                )
            },
            test = test,
        )
    }
}
