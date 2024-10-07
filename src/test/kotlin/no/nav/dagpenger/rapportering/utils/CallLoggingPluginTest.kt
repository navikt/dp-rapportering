package no.nav.dagpenger.rapportering.utils

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import no.nav.dagpenger.rapportering.api.ApiTestSetup
import no.nav.dagpenger.rapportering.api.doGet
import no.nav.dagpenger.rapportering.api.doPost
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.connector.toAdapterRapporteringsperiode
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.KallLogg
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import org.junit.jupiter.api.Test

class CallLoggingPluginTest : ApiTestSetup() {
    private val ident = "0102031234"
    private val rapporteringsperiode = rapporteringsperiodeFor(id = 123L)
    private val rapporteringsperiodeString = defaultObjectMapper.writeValueAsString(rapporteringsperiode)
    private val sendinnResponse =
        defaultObjectMapper.writeValueAsString(
            InnsendingResponse(id = 123L, status = "OK", feil = emptyList()),
        )
    private val personResponse = defaultObjectMapper.writeValueAsString(Person(1L, "TESTESSEN", "TEST", "NO", "EMELD"))

    @Test
    fun `Kan lagre get request og response`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
            }

            val path = "/rapporteringsperioder"
            client.doGet(path, issueToken(ident))

            val list = getLogList()

            list.size shouldBe 2
            list[0].type shouldBe "REST"
            list[0].kallRetning shouldBe "INN"
            list[0].method shouldBe "GET"
            list[0].operation shouldBe path
            list[0].status shouldBe 200
            list[0].request shouldStartWith "GET localhost:80$path HTTP/1.1"
            list[0].response shouldStartWith "200 OK"
            list[0].ident shouldBe ident
            list[0].logginfo shouldBe ""

            list[1].type shouldBe "REST"
            list[1].kallRetning shouldBe "UT"
            list[1].method shouldBe "GET"
            list[1].operation shouldBe path
            list[1].status shouldBe 200
            list[1].request shouldStartWith "GET https://meldeplikt-adapter:443/rapporteringsperioder"
            list[1].response.trimIndent() shouldBe
                """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Length: ${rapporteringsperiodeString.length + 2}

                [$rapporteringsperiodeString]
                """.trimIndent()
            list[1].ident shouldBe ident
            list[1].logginfo shouldBe ""
        }

    @Test
    fun `Kan lagre post request og response`() =
        setUpTestApplication {
            externalServices {
                meldepliktAdapter()
                pdfGenerator()
            }

            val adapterRapporteringsperiodeString =
                defaultObjectMapper.writeValueAsString(
                    rapporteringsperiode.toAdapterRapporteringsperiode(),
                )

            // Lagrer perioden i databasen
            client.doPost("/rapporteringsperiode/123/start", issueToken(ident))

            val path = "/rapporteringsperiode"
            client.doPost(path, issueToken(ident), rapporteringsperiode)

            val list = getLogList()

            list.size shouldBe 7
            list[2].type shouldBe "REST"
            list[2].kallRetning shouldBe "INN"
            list[2].method shouldBe "POST"
            list[2].operation shouldBe path
            list[2].status shouldBe 200
            list[2].request shouldStartWith "POST localhost:80$path HTTP/1.1"
            list[2].request shouldContain rapporteringsperiodeString
            list[2].response shouldStartWith "200 OK"
            list[2].ident shouldBe ident
            list[2].logginfo shouldBe ""

            list[3].type shouldBe "REST"
            list[3].kallRetning shouldBe "UT"
            list[3].method shouldBe "POST"
            list[3].operation shouldBe "/sendinn"
            list[3].status shouldBe 200
            list[3].request shouldStartWith "POST https://meldeplikt-adapter:443/sendinn"
            list[3].request shouldContain adapterRapporteringsperiodeString
            list[3].response.trimIndent() shouldBe
                """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Length: ${sendinnResponse.length}
                
                $sendinnResponse
                """.trimIndent()
            list[3].ident shouldBe ident
            list[3].logginfo shouldBe ""

            list[4].type shouldBe "REST"
            list[4].kallRetning shouldBe "UT"
            list[4].method shouldBe "GET"
            list[4].operation shouldBe "/person"
            list[4].status shouldBe 200
            list[4].request shouldStartWith "GET https://meldeplikt-adapter:443/person"
            list[4].response.trimIndent() shouldBe
                """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Length: ${personResponse.length}
                
                $personResponse
                """.trimIndent()
            list[4].ident shouldBe ident
            list[4].logginfo shouldBe ""

            list[5].type shouldBe "REST"
            list[5].kallRetning shouldBe "UT"
            list[5].method shouldBe "POST"
            list[5].operation shouldBe "/convert-html-to-pdf/meldekort"
            list[5].status shouldBe 200
            list[5].request shouldStartWith "POST https://pdf-generator:443/convert-html-to-pdf/meldekort"
            list[5].request shouldContain "Meldekort %RAPPORTERINGSPERIODE_ID%"
            list[5].response.trimIndent() shouldBe
                """
                HTTP/1.1 200 OK
                Content-Type: application/pdf
                Content-Length: 3
                
                PDF
                """.trimIndent()
            list[5].ident shouldBe "" // Det finnes ikke token når vi genererer PDF
            list[5].logginfo shouldBe ""

            list[6].type shouldBe "KAFKA"
            list[6].kallRetning shouldBe "UT"
            list[6].method shouldBe "PUBLISH"
            list[6].operation shouldBe "teamdagpenger.rapid.v1"
            list[6].status shouldBe 500
            list[6].request shouldContain ""
            list[6].response shouldBe ""
            list[6].ident shouldBe ident
            list[6].logginfo shouldBe ""
        }

    private fun getLogList() =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM kall_logg ORDER BY tidspunkt",
                ).map {
                    KallLogg(
                        it.string("korrelasjon_id"),
                        it.localDateTime("tidspunkt"),
                        it.string("type"),
                        it.string("kall_retning"),
                        it.string("method"),
                        it.string("operation"),
                        it.int("status"),
                        it.long("kalltid"),
                        it.string("request"),
                        it.string("response"),
                        it.string("ident"),
                        it.string("logginfo"),
                    )
                }.asList,
            )
        }

    private fun ExternalServicesBuilder.meldepliktAdapter() {
        hosts("https://meldeplikt-adapter") {
            routing {
                get("/rapporteringsperioder") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(defaultObjectMapper.writeValueAsString(listOf(rapporteringsperiode)))
                }
                get("/person") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(personResponse)
                }
                post("/sendinn") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(sendinnResponse)
                }
            }
        }
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
}
