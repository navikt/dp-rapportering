package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.DokumentInfo
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Korrigert
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
    fun `Kan sende rapporteringsperiode`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
                dokarkiv()
            }

            // Lagrer perioden i databasen
            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            with(client.doPost("/rapporteringsperiode", issueToken(fnr), rapporteringsperiodeFor())) {
                status shouldBe HttpStatusCode.OK
            }
        }

    @Test
    fun `innsending av rapporteringsperiode uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.doPost("/rapporteringsperiode", null, rapporteringsperiodeFor())) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `kan hente rapporteringsperiode med id`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))

            response.httpResponse.status shouldBe HttpStatusCode.OK
            response.body.id shouldBe 123L
        }

    @Test
    fun `kan starte utfylling av rapporteringsperiode`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val startResponse = client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))
            startResponse.status shouldBe HttpStatusCode.OK

            val periodeResponse = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            with(periodeResponse.body) {
                id shouldBe 123L
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe null
            }
        }

    @Test
    fun `kan lagre om bruker ønsker å stå som arbeidssøker`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            val response =
                client.doPost("/rapporteringsperiode/123/arbeidssoker", issueToken(fnr), ArbeidssokerRequest(true))
            response.status shouldBe HttpStatusCode.NoContent

            val periodeResponse = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            periodeResponse.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponse.body) {
                id shouldBe 123L
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe true
            }
        }

    @Test
    fun `kan lagre aktivitet`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            val aktivitet = Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Arbeid, "PT7H30M")
            val dagMedAktivitet = Dag(LocalDate.now(), listOf(aktivitet), 0)
            val response = client.doPost("/rapporteringsperiode/123/aktivitet", issueToken(fnr), dagMedAktivitet)
            response.status shouldBe HttpStatusCode.NoContent

            val periodeResponse = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            periodeResponse.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponse.body) {
                id shouldBe 123L
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe null
                dager.first().aktiviteter.first() shouldBe aktivitet
            }
        }
    }

    @Test
    fun `Kan korrigere rapporteringsperiode`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response = client.doPostAndReceive<Rapporteringsperiode>("/rapporteringsperiode/125/korriger", issueToken(fnr))
            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                id shouldBe 321L
                dager.forEach { dag ->
                    dag.aktiviteter.forEach { aktivitet ->
                        aktivitet.type shouldBe AktivitetsType.Arbeid
                        aktivitet.timer shouldBe "PT7H30M"
                    }
                }
                status shouldBe Korrigert
                kanKorrigeres shouldBe false
                kanSendes shouldBe true
            }
        }
    }

    @Test
    fun `kan hente rapporteringsperioder`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client
                    .doGetAndReceive<List<Rapporteringsperiode>>("/rapporteringsperioder", issueToken(fnr))

            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                size shouldBe 2
                first().id shouldBe 123L
                last().id shouldBe 124L
            }
        }

    @Test
    fun `kan hente tidligere innsendte rapporteringsperioder`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client
                    .doGetAndReceive<List<Rapporteringsperiode>>("/rapporteringsperioder/innsendte", issueToken(fnr))

            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                size shouldBe 2
                first().id shouldBe 126L
                last().id shouldBe 125L
            }
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
                                    id = 125L,
                                    aktivitet = aktivitet.copy(uuid = UUID.randomUUID()),
                                    status = AdapterRapporteringsperiodeStatus.Innsendt,
                                ),
                                adapterRapporteringsperiode(
                                    id = 126L,
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
                    call.respond("321")
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
                    aktiviteter = aktivitet?.let { listOf(aktivitet.copy(uuid = UUID.randomUUID())) } ?: emptyList(),
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
