package no.nav.dagpenger.rapportering.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.ExternalServicesBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.ApplicationBuilder
import no.nav.dagpenger.rapportering.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.config.konfigurasjon
import no.nav.dagpenger.rapportering.connector.AnsvarligSystem
import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.repository.InnsendingtidspunktRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.JournalfoeringRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.KallLoggRepositoryPostgres
import no.nav.dagpenger.rapportering.repository.Postgres.dataSource
import no.nav.dagpenger.rapportering.repository.Postgres.database
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.repository.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.repository.RapporteringRepositoryPostgres
import no.nav.dagpenger.rapportering.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.service.JournalfoeringService
import no.nav.dagpenger.rapportering.service.KallLoggService
import no.nav.dagpenger.rapportering.service.MeldekortregisterService
import no.nav.dagpenger.rapportering.service.MeldepliktService
import no.nav.dagpenger.rapportering.service.PdlService
import no.nav.dagpenger.rapportering.service.PersonregisterService
import no.nav.dagpenger.rapportering.service.RapporteringService
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meldepliktMetrikker
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.meterRegistry
import no.nav.dagpenger.rapportering.utils.OutgoingCallLoggingPlugin
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.util.concurrent.CompletableFuture

open class ApiTestSetup {
    companion object {
        const val TOKENX_ISSUER_ID = "tokenx"
        const val AZURE_ISSUER_ID = "azure"
        const val REQUIRED_AUDIENCE = "tokenx"
        val TEST_PRIVATE_JWK =
            """
            {
                "kty":"RSA",
                "alg":"RS256",
                "use":"sig",
                "p":"_xCPvqs85ZZVg460Qfot26rQoNRPTOVDo5p4nqH3ep6BK_5TvoU5LFXd26W-1V1Lc5fcvvftClPOT201xgat4DVtliNtoc8od_tWr190A3AzbsAVFOx0nKa5uhLBxP9SsPM84llp6PXF6QTMGFiPYuoLDaQQqL1K4BbHq3ZzF2M",
                "q":"7QLqW75zkfSDrn5rMoF50WXyB_ysNx6-2SvaXKGXaOn80IR7QW5vwkleJnsdz_1kr04rJws2p4HBJjUFfSJDi1Dapj7tbIwb0a1szDs6Y2fAa3DlzgXZCkoE2TIrW6UITgs14pI_a7RasclE71FpoZ78XNBvj3NmZugkNLBvRjs",
                "d":"f7aT4poed8uKdcSD95mvbfBdb6X-M86d99su0c390d6gWwYudeilDugH9PMwqUeUhY0tdaRVXr6rDDIKLSE-uEyaYKaramev0cG-J_QWYJU2Lx-4vDGNHAE7gC99o1Ee_LXqMDCBawMYyVcSWx7PxGQfzhSsARsAIbkarO1sg9zsqPS4exSMbK8wyCTPgRbnkB32_UdZSGbdSib1jSYyyoAItZ8oZHiltVsZIlA97kS4AGPtozde043NC7Ik0uEzgB5qJ_tR7vW8MfDrBj6da2NrLh0UH-q28dooBO1vEu0rvKZIescXYk9lk1ZakHhhpZaLykDOGzxCpronzP3_kQ",
                "e":"AQAB",
                "qi":"9kMIR6pEoiwN3M6O0n8bnh6c3KbLMoQQ1j8_Zyir7ZIlmRpWYl6HtK0VnD88zUuNKTrQa7-jfE5uAUa0PubzfRqybACb4S3HIAuSQP00_yCPzCSRrbpGRDFqq-8eWVwI9VdiN4oqkaaWcL1pd54IDcHIbfk-ZtNtZgsOlodeRMo",
                "dp":"VUecSAvI2JpjDRFxg326R2_dQWi6-uLMsq67FY7hx8WnOqZWKaUxcHllLENGguAmkgd8bv1F6-YJXNUO3Z7uE8DJWyGNTkSNK1CFsy0fBOdGywi-A7jrZFT6VBRhZRRY-YDaInPyzUkfWsGX26wAhPnrqCvqxgBEQJhdOh7obDE",
                "dq":"7EUfw92T8EhEjUrRKkQQYEK0iGnGdBxePLiOshEUky3PLT8kcBHbr17cUJgjHBiKqofOVNnE3i9nkOMCWcAyfUtY7KmGndL-WIP-FYplpnrjQzgEnuENgEhRlQOCXZWjNcnPKdKJDqF4WAtAgSIznz6SbSQMUoDD8IoyraPFCck",
                "n":"7CU8tTANiN6W_fD9SP1dK2vQvCkf7-nwvBYe5CfANV0_Bb0ZmQb77FVVsl1beJ7EYLz3cJmL8Is1RCHKUK_4ydqihNjEWTyZiQoj1i67pkqk_zRvfQa9raZR4uZbuBxx7dWUoPC6fFH2F_psAlHW0zf90fsLvhB6Aqq3uvO7XXqo8qNl9d_JSG0Rg_2QUYVb0WKmPVbbhgwtkFu0Tyuev-VZ9IzTbbr5wmZwEUVY7YAi73pDJkcZt5r2WjOF_cuIXe-O2vwbOrRgmJfHO9--mVLdATnEyrb6q2oy_75h6JjP-R4-TD1hyoFFoE2gmj-kSS6Z_Gggljs3Aw7--Nh10Q"
            }
            """.trimIndent()

        var mockOAuth2Server = MockOAuth2Server()

        @BeforeAll
        @JvmStatic
        fun setup() {
            try {
                println("Start mockserver")
                mockOAuth2Server = MockOAuth2Server()
                mockOAuth2Server.start(8091)
            } catch (e: Exception) {
                println("Failed to start mockserver")
                println(e)
            }

            setEnvConfig()

            mockkObject(ApplicationBuilder.Companion)
            every { getRapidsConnection() } returns TestRapid()
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            println("Stopping mockserver")
            mockOAuth2Server.shutdown()
        }

        fun setEnvConfig() {
            System.setProperty("MELDEPLIKT_ADAPTER_HOST", "meldeplikt-adapter")
            System.setProperty("MELDEPLIKT_ADAPTER_AUDIENCE", REQUIRED_AUDIENCE)
            System.setProperty("PERSONREGISTER_HOST", "personregister")
            System.setProperty("PERSONREGISTER_AUDIENCE", "personregister_audience")
            System.setProperty("PDF_GENERATOR_URL", "https://pdf-generator")
            System.setProperty("DB_JDBC_URL", "${database.jdbcUrl}&user=${database.username}&password=${database.password}")
            System.setProperty("token-x.client-id", TOKENX_ISSUER_ID)
            System.setProperty("TOKEN_X_CLIENT_ID", TOKENX_ISSUER_ID)
            System.setProperty("TOKEN_X_PRIVATE_JWK", TEST_PRIVATE_JWK)
            System.setProperty("token-x.well-known-url", mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString())
            System.setProperty("TOKEN_X_WELL_KNOWN_URL", mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString())
            System.setProperty("azure-app.well-known-url", mockOAuth2Server.wellKnownUrl(AZURE_ISSUER_ID).toString())
            System.setProperty("AZURE_APP_WELL_KNOWN_URL", mockOAuth2Server.wellKnownUrl(AZURE_ISSUER_ID).toString())
            System.setProperty("AZURE_APP_CLIENT_ID", AZURE_ISSUER_ID)
            System.setProperty("AZURE_APP_CLIENT_SECRET", TEST_PRIVATE_JWK)
            System.setProperty("ARBEIDSSOKERREGISTER_RECORD_KEY_URL", "http://arbeidssokerregister_record_key_url/api/v1/record-key")
            System.setProperty("ARBEIDSSOKERREGISTER_RECORD_KEY_SCOPE", "api://test.scope.arbeidssokerregister_record_key/.default")
            System.setProperty("ARBEIDSSOKERREGISTER_OPPSLAG_URL", "http://arbeidssokerregister_oppslag_url/api/v1/arbeidssoekerperioder")
            System.setProperty("ARBEIDSSOKERREGISTER_OPPSLAG_SCOPE", "api://test.scope.arbeidssokerregister_oppslag/.default")
            System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
            System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
            System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
            System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
            System.setProperty("BEKREFTELSE_TOPIC", "BEKREFTELSE_TOPIC")
            System.setProperty("GITHUB_SHA", "some_sha")
            System.setProperty("UNLEASH_SERVER_API_URL", "http://localhost")
            System.setProperty("UNLEASH_SERVER_API_TOKEN", "token")
            System.setProperty("UNLEASH_SERVER_API_ENV", "development")
        }
    }

    fun setUpTestApplication(block: suspend ApplicationTestBuilder.() -> Unit) {
        runMigration()
        clean()

        testApplication {
            environment {
                config = mapAppConfig()
            }

            val httpClient =
                createClient {
                    install("OutgoingCallInterceptor") {
                        OutgoingCallLoggingPlugin().intercept(this)
                    }
                    install(ContentNegotiation) {
                        jackson {
                            registerModule(JavaTimeModule())
                            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        }
                    }
                }

            val meldepliktConnector = MeldepliktConnector(httpClient = httpClient, actionTimer = actionTimer)
            val meldepliktService = MeldepliktService(meldepliktConnector)
            val rapporteringRepository = RapporteringRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            val innsendingtidspunktRepository = InnsendingtidspunktRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            val journalfoeringRepository = JournalfoeringRepositoryPostgres(PostgresDataSourceBuilder.dataSource, actionTimer)
            val kallLoggRepository = KallLoggRepositoryPostgres(PostgresDataSourceBuilder.dataSource)
            val kallLoggService = KallLoggService(kallLoggRepository)

            val topicPartition = TopicPartition("test-topic", 1)
            val recordMetadata = RecordMetadata(topicPartition, 1L, 2, 3L, 4, 5)
            val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>()
            val slot = slot<ProducerRecord<Long, Bekreftelse>>()
            every { bekreftelseKafkaProdusent.send(capture(slot), any()) } answers {
                secondArg<Callback>().onCompletion(recordMetadata, null)
                CompletableFuture.completedFuture(recordMetadata)
            }

            val personregisterService = mockk<PersonregisterService>()
            coEvery { personregisterService.oppdaterPersonstatus(any(), any()) } just runs
            coEvery { personregisterService.erBekreftelseOvertatt(any(), any()) } returns true

            val meldekortregisterService = mockk<MeldekortregisterService>()
            coEvery { personregisterService.hentAnsvarligSystem(any(), any()) } returns AnsvarligSystem.ARENA

            val pdlService = mockk<PdlService>()
            coEvery { pdlService.hentNavn(any()) } returns "Test Testesen"

            val arbeidssoekerService =
                ArbeidssøkerService(
                    kallLoggService = kallLoggService,
                    personregisterService = personregisterService,
                    httpClient = httpClient,
                    bekreftelseKafkaProdusent = bekreftelseKafkaProdusent,
                )
            val rapporteringService =
                RapporteringService(
                    meldepliktService,
                    rapporteringRepository,
                    innsendingtidspunktRepository,
                    JournalfoeringService(
                        journalfoeringRepository,
                        kallLoggService,
                        pdlService,
                        httpClient,
                    ),
                    kallLoggService,
                    arbeidssoekerService,
                    personregisterService,
                    meldekortregisterService,
                )

            application {
                konfigurasjon(meterRegistry, kallLoggRepository)
                internalApi(meterRegistry)
                rapporteringApi(rapporteringService, personregisterService, meldepliktMetrikker)
            }

            block()
        }
    }

    private fun mapAppConfig(): MapApplicationConfig =
        MapApplicationConfig(
            "no.nav.security.jwt.issuers.size" to "1",
            "no.nav.security.jwt.issuers.0.issuer_name" to TOKENX_ISSUER_ID,
            "no.nav.security.jwt.issuers.0.discoveryurl" to mockOAuth2Server.wellKnownUrl(TOKENX_ISSUER_ID).toString(),
            "no.nav.security.jwt.issuers.0.accepted_audience" to REQUIRED_AUDIENCE,
            "ktor.environment" to "local",
        )

    private fun clean() {
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

    fun issueToken(ident: String): String =
        mockOAuth2Server
            .issueToken(
                TOKENX_ISSUER_ID,
                "myclient",
                DefaultOAuth2TokenCallback(
                    audience = listOf(REQUIRED_AUDIENCE),
                    claims = mapOf("pid" to ident, "acr" to "Level4"),
                ),
            ).serialize()

    fun ExternalServicesBuilder.pdfGenerator() {
        hosts("https://pdf-generator") {
            routing {
                post("/convert-html-to-pdf/meldekort") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                    call.respond("PDF")
                }
            }
        }
    }

    fun ExternalServicesBuilder.arbeidssokerregisterRecordKey() {
        hosts("http://arbeidssokerregister_record_key_url") {
            routing {
                post("/api/v1/record-key") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond("{ \"key\": 1 }")
                }
            }
        }
    }

    fun ExternalServicesBuilder.arbeidssokerregisterOppslag() {
        hosts("http://arbeidssokerregister_oppslag_url") {
            routing {
                post("/api/v1/arbeidssoekerperioder") {
                    call.response.header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    call.respond(
                        """
                        [
                          {
                            "periodeId": "68219fd0-98d1-4ae9-8ddd-19bca28de5ee",
                            "startet": {
                              "tidspunkt": "2025-02-04T10:15:30",
                              "utfoertAv": {
                                "type": "Type",
                                "id": "1"
                              },
                              "kilde": "Kilde",
                              "aarsak": "Årsak"
                            }
                          }
                        ]
                        """.trimIndent(),
                    )
                }
            }
        }
    }
}
