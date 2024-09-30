package no.nav.dagpenger.rapportering.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
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
import no.nav.dagpenger.rapportering.model.PeriodeId
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Endret
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.utils.desember
import no.nav.dagpenger.rapportering.utils.februar
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class RapporteringApiTest : ApiTestSetup() {
    private val fnr = "12345678910"

    // Sjekk om bruker har DP meldeplikt

    @Test
    fun `harMeldekort uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.doGet("/harmeldeplikt", null)) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `harMeldeplikt returnerer InternalServerError hvis feil i meldepliktAdapter`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(harMeldepliktResponseStatus = HttpStatusCode.InternalServerError, harMeldepliktResponse = "false")
            }

            val response = client.doGet("/harmeldeplikt", issueToken(fnr))

            response.status shouldBe HttpStatusCode.InternalServerError
        }

    @Test
    fun `Kan hente harMeldeplikt true`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(harMeldepliktResponse = "true")
            }

            val response = client.doGet("/harmeldeplikt", issueToken(fnr))

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "true"
        }

    @Test
    fun `Kan hente harMeldeplikt false`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(harMeldepliktResponse = "false")
            }

            val response = client.doGet("/harmeldeplikt", issueToken(fnr))

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldBe "false"
        }

    // Sende rapporteringsperiode

    @Test
    fun `Kan sende rapporteringsperiode`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
                pdfGenerator()
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
    fun `returnerer Bad Request og InnsendingResponse når innsending feilet med http status OK`() =
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
                status shouldBe HttpStatusCode.BadRequest
                val innsendingResponse = defaultObjectMapper.readValue<InnsendingResponse>(bodyAsText())
                innsendingResponse.id shouldBe 123L
                innsendingResponse.status shouldBe "FEIL"
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
            }
        }

    @Test
    fun `kan sende inn endring med begrunnelse`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
                pdfGenerator()
            }

            val endreResponse = client.doPost("/rapporteringsperiode/125/endre", issueToken(fnr))
            endreResponse.status shouldBe HttpStatusCode.OK
            val endretPeriode = objectMapper.readValue(endreResponse.bodyAsText(), Rapporteringsperiode::class.java)

            with(
                client.doPost(
                    "/rapporteringsperiode",
                    issueToken(fnr),
                    rapporteringsperiodeFor(
                        id = endretPeriode.id,
                        status = Endret,
                        begrunnelseEndring = "Endring",
                        originalId = endretPeriode.originalId,
                    ),
                ),
            ) {
                status shouldBe HttpStatusCode.OK
                println(bodyAsText())
                val periodeId = objectMapper.readValue<PeriodeId>(bodyAsText())
                periodeId.id shouldBe "123"
            }
        }

    @Test
    fun `kan ikke sende inn endring uten begrunnelse`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val endreResponse = client.doPost("/rapporteringsperiode/125/endre", issueToken(fnr))
            endreResponse.status shouldBe HttpStatusCode.OK
            val nyId = objectMapper.readValue(endreResponse.bodyAsText(), Rapporteringsperiode::class.java).id

            with(
                client.doPost(
                    "/rapporteringsperiode",
                    issueToken(fnr),
                    rapporteringsperiodeFor(id = nyId, status = Endret),
                ),
            ) {
                status shouldBe HttpStatusCode.BadRequest
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
    fun `hente rapporteringsperiode med id som ikke finnes gir 404`() =
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

    @Test
    fun `hent rapporteringsperiode med id henter ikke original periode hvis flagg er satt til false`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            // Lagrer perioden i databasen
            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            with(
                client.doGetAndReceive<Rapporteringsperiode>(
                    "/rapporteringsperiode/123",
                    issueToken(fnr),
                    listOf(Pair("hentOriginal", "false")),
                ),
            ) {
                httpResponse.status shouldBe HttpStatusCode.OK
                body.id shouldBe 123
            }
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

    // Slette alle aktiviteter
    @Test
    fun `kan slette alle aktiviteter i en periode`() {
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
                dager.first().aktiviteter.first() shouldBe aktivitet
            }

            client.doDelete("/rapporteringsperiode/123/aktiviteter", issueToken(fnr)).status shouldBe HttpStatusCode.NoContent

            val periodeResponseAfterDelete =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            periodeResponseAfterDelete.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponseAfterDelete.body) {
                id shouldBe 123L
                dager.first().aktiviteter shouldBe emptyList()
            }
        }
    }

    // Oppdater begrunnelse
    @Test
    fun `kan oppdatere begrunnelse`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val endringResponse = client.doPost("/rapporteringsperiode/125/endre", issueToken(fnr))
            endringResponse.status shouldBe HttpStatusCode.OK
            val endretPeriode = objectMapper.readValue(endringResponse.bodyAsText(), Rapporteringsperiode::class.java)

            val response =
                client.doPost(
                    "/rapporteringsperiode/${endretPeriode.id}/begrunnelse",
                    issueToken(fnr),
                    BegrunnelseRequest("Dette er en begrunnelse"),
                )
            response.status shouldBe HttpStatusCode.NoContent

            val periodeResponse =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/${endretPeriode.id}", issueToken(fnr))
            periodeResponse.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponse.body) {
                id shouldBe endretPeriode.id
                status shouldBe Endret
                bruttoBelop shouldBe null
                registrertArbeidssoker shouldBe null
                begrunnelseEndring shouldBe "Dette er en begrunnelse"
            }
        }
    }

    // Oppdater rapporteringstype
    @Test
    fun `kan oppdatere rapporteringstype`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            val response =
                client.doPost("/rapporteringsperiode/123/rapporteringstype", issueToken(fnr), RapporteringstypeRequest("harAktivitet"))
            response.status shouldBe HttpStatusCode.NoContent

            val periodeResponse =
                client.doGetAndReceive<Rapporteringsperiode>("/rapporteringsperiode/123", issueToken(fnr))
            periodeResponse.httpResponse.status shouldBe HttpStatusCode.OK
            with(periodeResponse.body) {
                id shouldBe 123L
                rapporteringstype shouldBe "harAktivitet"
            }
        }

    @Test
    fun `oppdater rapporteringstype feiler hvis perioden ikke finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(rapporteringsperioderResponse = emptyList())
            }

            val response =
                client.doPost("/rapporteringsperiode/123/rapporteringstype", issueToken(fnr), RapporteringstypeRequest("harIkkeAktivitet"))
            response.status shouldBe HttpStatusCode.InternalServerError
        }

    @Test
    fun `oppdater rapporteringstype feiler hvis rapporteringstypen er blank`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            client.doPost("/rapporteringsperiode/123/start", issueToken(fnr))

            val response =
                client.doPost("/rapporteringsperiode/123/rapporteringstype", issueToken(fnr), RapporteringstypeRequest(""))
            response.status shouldBe HttpStatusCode.BadRequest
        }

    // Endre rapporteringsperiode

    @Test
    fun `Kan endre rapporteringsperiode`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val response =
                client.doPostAndReceive<Rapporteringsperiode>("/rapporteringsperiode/125/endre", issueToken(fnr))
            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                id shouldNotBe 125L
                dager.forEach { dag ->
                    dag.aktiviteter.forEach { aktivitet ->
                        aktivitet.type shouldBe AktivitetsType.Arbeid
                        aktivitet.timer shouldBe "PT7H30M"
                    }
                }
                status shouldBe Endret
                kanEndres shouldBe false
                kanSendes shouldBe true
            }
        }
    }

    @Test
    fun `Kan endre rapporteringsperiode når original periode ligger i databasen`() {
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(
                    rapporteringsperioderResponse =
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
                )
            }

            client.doPost("/rapporteringsperiode/125/start", issueToken(fnr))

            val response =
                client.doPostAndReceive<Rapporteringsperiode>("/rapporteringsperiode/125/endre", issueToken(fnr))
            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                id shouldNotBe 125L
                dager.forEach { dag ->
                    dag.aktiviteter.forEach { aktivitet ->
                        aktivitet.type shouldBe AktivitetsType.Arbeid
                        aktivitet.timer shouldBe "PT7H30M"
                    }
                }
                status shouldBe Endret
                kanEndres shouldBe false
                kanSendes shouldBe true
            }
        }
    }

    @Test
    fun `endring feiler hvis original rapporteringsperiode ikke finnes`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(sendteRapporteringsperioderResponse = emptyList())
            }

            val response = client.doPost("/rapporteringsperiode/123/endre", issueToken(fnr))
            response.status shouldBe HttpStatusCode.InternalServerError
        }

    @Test
    fun `endring feiler hvis original rapporteringsperiode ikke kan endres`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter(sendteRapporteringsperioderResponse = listOf(adapterRapporteringsperiode(kanEndres = false)))
            }

            val response = client.doPost("/rapporteringsperiode/123/endre", issueToken(fnr))
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
                meldepliktAdapter(
                    sendteRapporteringsperioderResponse =
                        listOf(
                            adapterRapporteringsperiode(
                                id = 124L,
                                fraOgMed = 18.desember(2023),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 125L,
                                fraOgMed = 1.januar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 126L,
                                fraOgMed = 1.januar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                                begrunnelseEndring = "En god begrunnelse",
                            ),
                            adapterRapporteringsperiode(
                                id = 127L,
                                fraOgMed = LocalDate.now().plusDays(1),
                                aktivitet = defaultAdapterAktivitet,
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 128L,
                                fraOgMed = 15.januar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 129L,
                                fraOgMed = 29.januar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 130L,
                                fraOgMed = 12.februar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                            ),
                            adapterRapporteringsperiode(
                                id = 131L,
                                fraOgMed = 12.februar(2024),
                                aktivitet = defaultAdapterAktivitet.copy(uuid = UUID.randomUUID()),
                                status = AdapterRapporteringsperiodeStatus.Innsendt,
                                begrunnelseEndring = "En god begrunnelse",
                            ),
                        ),
                )
            }

            val response =
                client
                    .doGetAndReceive<List<Rapporteringsperiode>>("/rapporteringsperioder/innsendte", issueToken(fnr))

            println(response.body)

            response.httpResponse.status shouldBe HttpStatusCode.OK
            with(response.body) {
                size shouldBe 7
                this[0].id shouldBe 127L
                this[1].id shouldBe 131L
                this[2].id shouldBe 130L
                this[3].id shouldBe 129L
                this[4].id shouldBe 128L
                this[5].id shouldBe 126L
                this[6].id shouldBe 125L
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

    private fun ExternalServicesBuilder.pdfGenerator() {
        hosts("https://pdf-generator") {
            routing {
                post("/convert-html-to-pdf/meldekort") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                    call.respond("PDF")
                }
            }
        }
    }

    private val defaultAdapterAktivitet =
        AdapterAktivitet(
            uuid = UUID.randomUUID(),
            type = Arbeid,
            timer = 7.5,
        )

    private fun ExternalServicesBuilder.meldepliktAdapter(
        rapporteringsperioderResponse: List<AdapterRapporteringsperiode> =
            listOf(
                adapterRapporteringsperiode(),
                adapterRapporteringsperiode(id = 124L, fraOgMed = LocalDate.now().plusDays(1)),
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
        endreRapporteringsperiodeResponse: Long = 321L,
        endreRapporteringsperiodeResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        sendInnResponse: InnsendingResponse? = InnsendingResponse(id = 123L, status = "OK", feil = emptyList()),
        sendInnResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        personResponse: String = person(),
        personResponseStatus: HttpStatusCode = HttpStatusCode.OK,
        harMeldepliktResponse: String = "true",
        harMeldepliktResponseStatus: HttpStatusCode = HttpStatusCode.OK,
    ) {
        hosts("https://meldeplikt-adapter") {
            routing {
                get("/harmeldeplikt") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                    call.respond(harMeldepliktResponseStatus, harMeldepliktResponse)
                }
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
                get("/endrerapporteringsperiode/{id}") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        status = endreRapporteringsperiodeResponseStatus,
                        defaultObjectMapper.writeValueAsString(endreRapporteringsperiodeResponse),
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
        kanSendes: Boolean = true,
        kanEndres: Boolean = true,
        status: AdapterRapporteringsperiodeStatus = AdapterRapporteringsperiodeStatus.TilUtfylling,
        bruttoBelop: Double? = null,
        registrertArbeidssoker: Boolean? = null,
        begrunnelseEndring: String? = null,
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
        kanSendes = kanSendes,
        kanEndres = kanEndres,
        status = status,
        bruttoBelop = bruttoBelop,
        registrertArbeidssoker = registrertArbeidssoker,
        begrunnelseEndring = begrunnelseEndring,
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
