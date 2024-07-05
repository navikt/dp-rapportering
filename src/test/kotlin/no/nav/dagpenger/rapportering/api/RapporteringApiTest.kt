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
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet
import no.nav.dagpenger.rapportering.connector.AdapterAktivitet.AdapterAktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.connector.AdapterDag
import no.nav.dagpenger.rapportering.connector.AdapterPeriode
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.connector.AdapterRapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.DokumentInfo
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringApiTest : ApiTestSetup() {
    private val fnr = "12345678910"

    @AfterEach
    fun clean() {
        println("Cleaning database")
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "TRUNCATE TABLE aktivitet, dag, midlertidig_lagrede_journalposter, " +
                        "opprettede_journalposter, rapporteringsperiode, kall_logg",
                ).asExecute,
            )
        }
    }

    @Test
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
