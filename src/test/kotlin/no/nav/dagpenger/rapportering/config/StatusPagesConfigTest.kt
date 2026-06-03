package no.nav.dagpenger.rapportering.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.rapportering.exceptions.RapporteringsperiodeNotFoundException
import no.nav.dagpenger.rapportering.metrics.MeldepliktMetrikker
import org.junit.jupiter.api.Test

class StatusPagesConfigTest {
    @Test
    fun `statusPages returnerer forventet respons på ResponseException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw ResponseException(
                        mockk<HttpResponse>(relaxed = true).also {
                            every { it.status } returns InternalServerError
                        },
                        "Jeg er en ResponseException",
                    )
                }
            }

            val response = client.get("/throws-response-exception")

            response.status.value shouldBe InternalServerError.value
            response.bodyAsText() shouldContain "Jeg er en ResponseException"
        }

    @Test
    fun `statusPages returnerer forventet respons på JsonConvertException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw JsonConvertException("Jeg er en JsonConvertException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.status.value shouldBe InternalServerError.value
            response.bodyAsText() shouldContain "Jeg er en JsonConvertException"
        }

    @Test
    fun `statusPages returnerer forventet respons på IllegalArgumentException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw IllegalArgumentException("Jeg er en IllegalArgumentException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.bodyAsText()
            response.status.value shouldBe BadRequest.value
            response.bodyAsText() shouldContain "Jeg er en IllegalArgumentException"
        }

    @Test
    fun `statusPages returnerer forventet respons på NotFoundException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw NotFoundException("Jeg er en NotFoundException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.bodyAsText()
            response.status.value shouldBe NotFound.value
            response.bodyAsText() shouldContain "Jeg er en NotFoundException"
        }

    @Test
    fun `statusPages returnerer forventet respons på RapporteringsperiodeNotFoundException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw RapporteringsperiodeNotFoundException("Jeg er en RapporteringsperiodeNotFoundException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.bodyAsText()
            response.status.value shouldBe NotFound.value
            response.bodyAsText() shouldContain "Jeg er en RapporteringsperiodeNotFoundException"
        }

    @Test
    fun `statusPages returnerer forventet respons på BadRequestException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw BadRequestException("Jeg er en BadRequestException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.bodyAsText()
            response.status.value shouldBe BadRequest.value
            response.bodyAsText() shouldContain "Jeg er en BadRequestException"
        }

    @Test
    fun `statusPages returnerer forventet respons på en annen exception enn de vi har eksplisitt håndtering av, feks RuntimeException`() =
        testApplication {
            installPlugins()
            routing {
                get("/throws-response-exception") {
                    throw RuntimeException("Jeg er en RuntimeException")
                }
            }

            val response = client.get("/throws-response-exception")

            response.bodyAsText()
            response.status.value shouldBe InternalServerError.value
            response.bodyAsText() shouldContain "Jeg er en RuntimeException"
        }

    private fun ApplicationTestBuilder.installPlugins() {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(ObjectMapper()))
        }
        install(StatusPages) {
            statusPagesConfig(mockk<MeldepliktMetrikker>(relaxed = true))
        }
    }
}
