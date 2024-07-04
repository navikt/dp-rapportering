package no.nav.dagpenger.rapportering.api

import com.fasterxml.jackson.core.type.TypeReference
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ExternalServicesBuilder
import no.nav.dagpenger.rapportering.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.connector.AdapterDag
import no.nav.dagpenger.rapportering.connector.AdapterPeriode
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.DokumentInfo
import no.nav.dagpenger.rapportering.model.InnsendingFeil
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate
import java.util.UUID

@TestMethodOrder(OrderAnnotation::class)
class RapporteringApiTest : ApiTestSetup() {
    private val fnr = "12345678910"

    @Test
    @Order(Integer.MIN_VALUE)
    fun `Kan hente rapporteringsperioder`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client.get("/rapporteringsperioder") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(fnr)}")
                }

            response.status shouldBe HttpStatusCode.OK
            with(
                response.bodyAsText().let {
                    println("/rapporteringsperioder body: $it")
                    defaultObjectMapper.readValue(it, object : TypeReference<List<Rapporteringsperiode>>() {})
                },
            ) {
                size shouldBe 2
                first().id shouldBe 123L
                last().id shouldBe 124L
            }
        }

    @Test
    @Order(1)
    fun `kan hente rapporteringsperiode med id`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client.get("/rapporteringsperiode/123") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(fnr)}")
                }

            response.status shouldBe HttpStatusCode.OK
            with(
                response.bodyAsText().let {
                    defaultObjectMapper.readValue(it, Rapporteringsperiode::class.java)
                },
            ) {
                id shouldBe 123L
            }
        }

    @Test
    @Order(2)
    fun `innsending av rapporteringsperiode uten token gir unauthorized`() =
        setUpTestApplication {
            with(
                client.post("/rapporteringsperiode") {
                    header("Content-Type", "application/json")
                    setBody(defaultObjectMapper.writeValueAsString(rapporteringsperiodeFor()))
                },
            ) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    @Order(3)
    fun `Kan sende rapporteringsperiode`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
                dokarkiv()
            }

            // Lagrer perioden i databasen
            client.post("/rapporteringsperiode/123/start") {
                header(HttpHeaders.Authorization, "Bearer ${issueToken(fnr)}")
            }

            with(
                client.post("/rapporteringsperiode") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(fnr)}")
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    header(HttpHeaders.ContentType, ContentType.Application.Json)
                    setBody(defaultObjectMapper.writeValueAsString(rapporteringsperiodeFor()))
                },
            ) {
                status shouldBe HttpStatusCode.OK
            }
        }

    fun ExternalServicesBuilder.dokarkiv() {
        hosts("https://dokarkiv") {
            routing {
                post("/rest/journalpostapi/v1/journalpost") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(journalpostResponse())
                }
            }
        }
    }

    fun ExternalServicesBuilder.meldepliktAdapter() {
        hosts("https://meldeplikt-adapter") {
            routing {
                get("/rapporteringsperioder") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        defaultObjectMapper.writeValueAsString(
                            listOf(
                                adapterRapporteringsperiode(),
                                adapterRapporteringsperiode(id = 124L, fraOgMed = LocalDate.now().plusDays(1)),
                            ),
                        ),
                    )
                }
                get("/sendterapporteringsperioder") {
                    val aktivitet =
                        AdapterAktivitet(
                            uuid = UUID.randomUUID(),
                            type = Arbeid,
                            timer = 7.5,
                        )
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        defaultObjectMapper.writeValueAsString(
                            listOf(
                                adapterRapporteringsperiode(
                                    aktivitet = aktivitet.copy(uuid = UUID.randomUUID()),
                                    status = AdapterRapporteringsperiodeStatus.Innsendt,
                                ),
                                adapterRapporteringsperiode(
                                    id = 124L,
                                    fraOgMed = LocalDate.now().plusDays(1),
                                    aktivitet = aktivitet,
                                    status = AdapterRapporteringsperiodeStatus.Innsendt,
                                ),
                            ),
                        ),
                    )
                }
                get("/korrigerrapporteringsperiode/{id}") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(321L)
                }
                post("/sendinn") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        defaultObjectMapper.writeValueAsString(
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
    }

    fun rapporteringsperiodeFor(
        id: Long = 123L,
        fraOgMed: LocalDate = LocalDate.now().minusDays(13),
        tilOgMed: LocalDate = fraOgMed.plusDays(13),
        aktivitet: Aktivitet? = null,
        kanSendes: Boolean = true,
        kanKorrigeres: Boolean = true,
        status: RapporteringsperiodeStatus = TilUtfylling,
        bruttoBelop: String? = null,
        registrertArbeidssoker: Boolean? = null,
    ) = Rapporteringsperiode(
        id = id,
        periode = Periode(fraOgMed = fraOgMed, tilOgMed = tilOgMed),
        dager =
            (0..13).map {
                Dag(
                    dato = fraOgMed.plusDays(it.toLong()),
                    aktiviteter = aktivitet?.let { listOf(aktivitet) } ?: emptyList(),
                    dagIndex = it,
                )
            },
        kanSendesFra = tilOgMed.minusDays(1),
        kanSendes = kanSendes,
        kanKorrigeres = kanKorrigeres,
        status = status,
        bruttoBelop = bruttoBelop?.toDouble(),
        registrertArbeidssoker = registrertArbeidssoker,
    )

    fun adapterRapporteringsperiode(
        id: Long = 123L,
        fraOgMed: LocalDate = LocalDate.now().minusDays(13),
        tilOgMed: LocalDate = fraOgMed.plusDays(13),
        aktivitet: AdapterAktivitet? = null,
        status: AdapterRapporteringsperiodeStatus = AdapterRapporteringsperiodeStatus.TilUtfylling,
    ) = AdapterRapporteringsperiode(
        id = id,
        periode =
            AdapterPeriode(
                fraOgMed = fraOgMed,
                tilOgMed = tilOgMed,
            ),
        dager =
            (0..13).map {
                AdapterDag(
                    dato = fraOgMed.plusDays(it.toLong()),
                    aktiviteter = aktivitet?.let { listOf(aktivitet) } ?: emptyList(),
                    dagIndex = it,
                )
            },
        kanSendesFra = tilOgMed.minusDays(1),
        kanSendes = true,
        kanKorrigeres = true,
        status = status,
        bruttoBelop = null,
        registrertArbeidssoker = null,
    )

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
          "dokumenter": ${defaultObjectMapper.writeValueAsString(dokumenter)}
        }
        """.trimIndent()
}
