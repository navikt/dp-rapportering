package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
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
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Korrigert
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringApiTest : ApiTestSetup() {
    private val fnr = "12345678910"

    // Sende rapporteringsperiode

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
    fun `sender ikke inn hvis perioden ikke kan sendes`() =
        setUpTestApplication {
            with(client.doPost("/rapporteringsperiode", issueToken(fnr), rapporteringsperiodeFor(kanSendes = false))) {
                status shouldBe HttpStatusCode.BadRequest
            }
        }

    @Test
    fun `returnerer feil hvis innsending feilet med http status OK`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(
                    sendInnResponse =
                        InnsendingResponse(
                            id = 123L,
                            status = "FEIL",
                            feil = listOf(InnsendingFeil("kode", listOf("param1", "param2"))),
                        ),
                )
            }

            with(client.doPost("/rapporteringsperiode", issueToken(fnr), rapporteringsperiodeFor())) {
                status shouldBe HttpStatusCode.InternalServerError
            }
        }

    @Test
    fun `returnerer feil hvis innsending feilet uten http status OK`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(
                    sendInnResponseStatus = HttpStatusCode.InternalServerError,
                    sendInnResponse = null,
                )
            }

            with(client.doPost("/rapporteringsperiode", issueToken(fnr), rapporteringsperiodeFor())) {
                status shouldBe HttpStatusCode.InternalServerError
                println("Body: ${body<String>()}")
            }
        }

    // Hente rapporteringsperiode med id

    @Test
    fun `kan hente rapporteringsperiode med id som ikke er sendt`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(sendteRapporteringsperioderResponse = emptyList())
            }

            val response = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))

            response.httpResponse.status shouldBe HttpStatusCode.OK
            response.body.id shouldBe 123L
        }

    @Test
    fun `kan hente rapporteringsperiode med id som er sendt`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response = client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/126", issueToken(fnr))

            response.httpResponse.status shouldBe HttpStatusCode.OK
            response.body.id shouldBe 126L
        }

    @Test
    fun `hente rapporteringsperiode med id som ikke funnes gir 404`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(
                    rapporteringsperioderResponse = emptyList(),
                    sendteRapporteringsperioderResponse = emptyList(),
                )
            }

            val response = client.doGet("/rapporteringsperiode/123", issueToken(fnr))

            response.status shouldBe HttpStatusCode.NotFound
        }

    // Start ufylling av rapporteringsperiode (aka lagre perioden i databasen)

    @Test
    fun `kan starte utfylling av rapporteringsperiode`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val startResponse = client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))
            startResponse.status shouldBe HttpStatusCode.OK

            val periodeResponse =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            with(periodeResponse.body) {
                id shouldBe 123L
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe null
            }
        }

    @Test
    fun `start returnerer 500 hvis perioden som skal startes ikke finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val startResponse = client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))
            startResponse.status shouldBe HttpStatusCode.InternalServerError
        }

    // Lagre om bruker ønsker å stå som arbeidssøker

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

            val periodeResponse =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            periodeResponse.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponse.body) {
                id shouldBe 123L
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe true
            }
        }

    @Test
    fun `oppdater arbeidssøkerstatus feiler hvis request ikke stemmer`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response =
                client.doPost(
                    "/rapporteringsperiode/123/arbeidssoker",
                    issueToken(fnr),
                    """{"registrertArbeidssoker": null}""".trimIndent(),
                )
            response.status shouldBe HttpStatusCode.BadRequest
        }

    @Test
    fun `oppdater arbeidssøkerstatus feiler hvis perioden ikke finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response =
                client.doPost("/rapporteringsperiode/123/arbeidssoker", issueToken(fnr), ArbeidssokerRequest(true))
            response.status shouldBe HttpStatusCode.InternalServerError
        }

    // Lagre dag og aktivitet

    @Test
    fun `kan lagre aktivitet`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            val aktivitet = Aktivitet(UUID.randomUUID(), AktivitetsType.Arbeid, "PT7H30M")
            val dagMedAktivitet = Dag(LocalDate.now(), listOf(aktivitet), 0)
            val response = client.doPost("/rapporteringsperiode/123/aktivitet", issueToken(fnr), dagMedAktivitet)
            response.status shouldBe HttpStatusCode.NoContent

            val periodeResponse =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
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
    fun `lagre aktivitet feiler hvis perioden ikke finnes`() {
        setUpTestApplication {
            val aktivitet = Aktivitet(UUID.randomUUID(), AktivitetsType.Arbeid, "PT7H30M")
            val dagMedAktivitet = Dag(LocalDate.now(), listOf(aktivitet), 0)
            val response = client.doPost("/rapporteringsperiode/123/aktivitet", issueToken(fnr), dagMedAktivitet)
            response.status shouldBe HttpStatusCode.InternalServerError
        }
    }

    // Korriger rapporteringsperiode

    @Test
    fun `Kan korrigere rapporteringsperiode`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client.doPostAndReceive<Rapporteringsperiode>("/rapporteringsperiode/125/korriger", issueToken(fnr))
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
    fun `korrigering feiler hvis original rapporteringsperiode ikke finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response = client.doPost("/rapporteringsperiode/123/korriger", issueToken(fnr))
            response.status shouldBe HttpStatusCode.InternalServerError
        }

    @Test
    fun `korrigering feiler hvis original rapporteringsperiode ikke kan korrigeres`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = listOf(rapporteringsperiodeFor(kanKorrigeres = false)))
            }

            val response = client.doPost("/rapporteringsperiode/123/korriger", issueToken(fnr))
            response.status shouldBe HttpStatusCode.BadRequest
        }

    // Hente rapporteringsperioder

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
    fun `hente rapporteringsperioder propagerer 204 fra dapter`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponseStatus = HttpStatusCode.NoContent)
            }

            val response = client.doGet("/rapporteringsperioder", issueToken(fnr))
            response.status shouldBe HttpStatusCode.NoContent
        }

    @Test
    fun `hente rapporteringsperioder gir 204 hvis ingen perioder`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response = client.doGet("/rapporteringsperioder", issueToken(fnr))
            response.status shouldBe HttpStatusCode.NoContent
        }

    // Hente innsendte rapporteringsperioder

    @Test
    fun `kan hente tidligere innsendte rapporteringsperioder`() =
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

    @Test
    fun `tidligere innsendte rapporteringsperioder gir 204 hvis ingen perioder finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(sendteRapporteringsperioderResponse = emptyList())
            }

            val response = client.doGet("/rapporteringsperioder/innsendte", issueToken(fnr))
            response.status shouldBe HttpStatusCode.NoContent
        }

    private fun ExternalServicesBuilder.dokarkiv() {
        hosts("https://dokarkiv") {
            routing {
                post("/rest/journalpostapi/v1/journalpost") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(journalpostResponse())
                }
            }
        }
    }

    val defaultAdapterAktivitet =
        AdapterAktivitet(
            uuid = UUID.randomUUID(),
            type = Arbeid,
            timer = 7.5,
        )

    fun ExternalServicesBuilder.meldepliktAdapter(
        rapporteringsperioderResponse: List<Rapporteringsperiode> =
            listOf(
                rapporteringsperiodeFor(),
                rapporteringsperiodeFor(id = 124L, fraOgMed = LocalDate.now().plusDays(1)),
            ),
        rapporteringsperioderResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        sendteRapporteringsperioderResponse: List<AdapterRapporteringsperiode> =
            listOf(
                adapterRapporteringsperiode(
                    id = 125L,
                    aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                    status = AdapterRapporteringsperiodeStatus.Innsendt,
                ),
                adapterRapporteringsperiode(
                    id = 126L,
                    fraOgMed = LocalDate.now().plusDays(1),
                    aktivitet = defaultAdapterAktivitet,
                    status = AdapterRapporteringsperiodeStatus.Innsendt,
                ),
            ),
        sendteRapporteringsperioderResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        korrigerRapporteringsperiodeResponse: Long = 321L,
        korrigerRapporteringsperiodeResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        sendInnResponse: InnsendingResponse? = InnsendingResponse(id = 123L, status = "OK", feil = emptyList()),
        sendInnResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        personResponse: String = person(),
        personResponseStatus: HttpStatusCode = HttpStatusCode.OK,
    ) {
        hosts("https://meldeplikt-adapter") {
            routing {
                get("/rapporteringsperioder") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        status = rapporteringsperioderResponseStatus,
                        defaultObjectMapper.writeValueAsString(rapporteringsperioderResponse),
                    )
                }
                get("/sendterapporteringsperioder") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        status = sendteRapporteringsperioderResponseStatus,
                        defaultObjectMapper.writeValueAsString(sendteRapporteringsperioderResponse),
                    )
                }
                get("/korrigerrapporteringsperiode/{id}") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        status = korrigerRapporteringsperiodeResponseStatus,
                        defaultObjectMapper.writeValueAsString(korrigerRapporteringsperiodeResponse),
                    )
                }
                post("/sendinn") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        status = sendInnResponseStatus,
                        defaultObjectMapper.writeValueAsString(sendInnResponse),
                    )
                }
                get("/person") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(status = personResponseStatus, personResponse)
                }
            }
        }
    }

    private fun adapterRapporteringsperiode(
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

    private fun person(
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

    private fun journalpostResponse(
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
