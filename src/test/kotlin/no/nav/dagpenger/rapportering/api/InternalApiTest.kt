package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class InternalApiTest : ApiTestSetup() {
    @Test
    fun `isAlive svarer OK`() =
        setUpTestApplication {
            with(client.get("/isalive")) {
                status shouldBe HttpStatusCode.OK
            }
        }

    @Test
    fun `isReady svarer OK`() =
        setUpTestApplication {
            with(client.get("/isready")) {
                status shouldBe HttpStatusCode.OK
            }
        }
}
