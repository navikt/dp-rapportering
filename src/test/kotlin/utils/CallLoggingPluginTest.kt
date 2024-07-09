package utils

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
import no.nav.dagpenger.rapportering.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.api.ApiTestSetup
import no.nav.dagpenger.rapportering.api.doGet
import no.nav.dagpenger.rapportering.api.doPost
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import no.nav.dagpenger.rapportering.model.InnsendingResponse
import no.nav.dagpenger.rapportering.model.KallLogg
import no.nav.dagpenger.rapportering.model.Person
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class CallLoggingPluginTest : ApiTestSetup() {
    private val ident = "0102031234"
    private val rapporteringsperiode = rapporteringsperiodeFor(id = 123L)
    private val rapporteringsperiodeString = defaultObjectMapper.writeValueAsString(rapporteringsperiode)

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
                Content-Length: 953

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
            }

            // Lagrer perioden i databasen
            client.doPost("/rapporteringsperiode/123/start", issueToken(ident))

            val path = "/rapporteringsperiode"
            client.doPost(path, issueToken(ident), rapporteringsperiode)

            val list = getLogList()

            list.size shouldBe 5
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
            list[3].request shouldContain rapporteringsperiodeString
            list[3].response.trimIndent() shouldBe
                """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Length: 34
                
                {"id":123,"status":"OK","feil":[]}
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
                Content-Length: 95
                
                {"personId":1,"etternavn":"TESTESSEN","fornavn":"TEST","maalformkode":"NO","meldeform":"EMELD"}
                """.trimIndent()
            list[4].ident shouldBe ident
            list[4].logginfo shouldBe ""
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
                    call.respond(defaultObjectMapper.writeValueAsString(Person(1L, "TESTESSEN", "TEST", "NO", "EMELD")))
                }
                post("/sendinn") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        defaultObjectMapper.writeValueAsString(
                            InnsendingResponse(id = 123L, status = "OK", feil = emptyList()),
                        ),
                    )
                }
            }
        }
    }
}
