package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.Configuration
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.DokumentInfo
import no.nav.dagpenger.rapportering.model.InnsendingFeil
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringApiTest : ApiTestSetup() {
    private val fnr = "12345678910"

    @Test
    fun `innsending av rapporteringsperiode uten token gir unauthorized`() =
        setUpTestApplication {
            with(
                client.post("/rapporteringsperiode") {
                    header("Content-Type", "application/json")
                    setBody(rapporteringsperiodeFor())
                },
            ) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `fff`() =
        setUpTestApplication {
            // TODO: Må legge til rapporteringsperioden i databasen først
            externalServices {
                hosts("https://meldeplikt-adapter") {
                    routing {
                        post("/sendinn") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(
                                Configuration.defaultObjectMapper.writeValueAsString(
                                    InnsendingResponse(id = 123L, status = "OK", feil = emptyList()),
                                ),
                            )
                        }
                        get("/person") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(person())
                        }
                    }
                }
                hosts("https://dokarkiv") {
                    routing {
                        post("/rest/journalpostapi/v1/journalpost") {
                            call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            call.respond(journalpostResponse())
                        }
                    }
                }
            }

            println("Rapporteringsperiode: ${rapporteringsperiodeFor()}")

            with(
                client.post("/rapporteringsperiode") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(fnr)}")
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(rapporteringsperiodeFor())
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }
        }

    fun rapporteringsperiodeFor(
        id: Long = 123L,
        fraOgMed: LocalDate = LocalDate.now().minusDays(13),
        tilOgMed: LocalDate = LocalDate.now(),
        dager: String = aktivitetsdagerlisteFor(startDato = fraOgMed),
        kanSendesFra: LocalDate = LocalDate.now(),
        kanSendes: Boolean = true,
        kanKorrigeres: Boolean = true,
        status: RapporteringsperiodeStatus = TilUtfylling,
        bruttoBelop: String? = null,
    ) = //language=JSON
        """
        {
          "id": $id,
          "periode": {
            "fraOgMed": "$fraOgMed",
            "tilOgMed": "$tilOgMed"
          },
          "dager": $dager,
          "kanSendesFra": "$kanSendesFra",
          "kanSendes": $kanSendes,
          "kanKorrigeres": $kanKorrigeres,
          "bruttoBelop": $bruttoBelop,
          "status": "${status.name}"
        }
        """.trimIndent()

    fun aktivitetsdagerlisteFor(
        startDato: LocalDate = LocalDate.now().minusWeeks(2),
        aktivitet: String? = null,
    ) = //language=JSON
        """
        [
            {
                "dato": "$startDato",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 0
            },
            {
                "dato": "${startDato.plusDays(1)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 1
            },
            {
                "dato": "${startDato.plusDays(2)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 2
            },
            {
                "dato": "${startDato.plusDays(3)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 3
            },
            {
                "dato": "${startDato.plusDays(4)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 4
            },
            {
                "dato": "${startDato.plusDays(5)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 5
            },
            {
                "dato": "${startDato.plusDays(6)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 6
            },
            {
                "dato": "${startDato.plusDays(7)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 7
            },
            {
                "dato": "${startDato.plusDays(8)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 8
            },
            {
                "dato": "${startDato.plusDays(9)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 9
            },
            {
                "dato": "${startDato.plusDays(10)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 10
            },
            {
                "dato": "${startDato.plusDays(11)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 11
            },
            {
                "dato": "${startDato.plusDays(12)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 12
            },
            {
                "dato": "${startDato.plusDays(13)}",
                "aktiviteter": ${aktivitet ?: "[]"},
                "dagIndex": 13
            }
        ]
        """.trimIndent()

    fun aktivitetsliste(
        type: AktivitetsType,
        timer: Double? = null,
    ) = //language=JSON
        """
        [
          {
            "uuid": "${UUID.randomUUID()}",
            "type": "$type",
            "timer": $timer
          }
        ]
        """.trimIndent()

    fun innsendingResponse(
        id: Long = 123L,
        status: String = "OK",
        feil: List<InnsendingFeil> = emptyList(),
    ) = // language=JSON
        """
        {
          "id": $id,
          "status": "$status",
          "feil": $feil
        }
        """.trimIndent()

    fun person(
        personId: Long = 123L,
        etternavn: String = "Nordmann",
        fornavn: String = "Kari",
        maalformkode: String = "N",
        meldeform: String = "D",
    ) = // language=JSON
        """
        {
          "personId": $personId,
          "etternavn": "$etternavn",
          "fornavn": "$fornavn",
          "maalformkode": "$maalformkode",
          "meldeform": "$meldeform"
        }
        """.trimIndent()

    fun journalpostResponse(
        journalpostId: Long = 123L,
        journalstatus: String = "MOTTATT",
        journalpostferdigstilt: Boolean = true,
        dokumenter: List<DokumentInfo> = listOf(DokumentInfo(dokumentInfoId = 1L)),
    ) = // language=JSON
        """
        {
          "journalpostId": $journalpostId,
          "journalstatus": "$journalstatus",
          "journalpostferdigstilt": $journalpostferdigstilt,
          "dokumenter": ${Configuration.defaultObjectMapper.writeValueAsString(dokumenter)}
        }
        """.trimIndent()
}
