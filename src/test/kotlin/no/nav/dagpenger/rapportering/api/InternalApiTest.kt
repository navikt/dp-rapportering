package no.nav.dagpenger.rapportering.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class InternalApiTest : ApiTestSetup() {
    @Test
    fun testInternalApi() =
        setUpTestApplication {
            with(client.get("/isAlive")) {
                status shouldBe HttpStatusCode.OK
            }

            with(client.get("/isReady")) {
                status shouldBe HttpStatusCode.OK
            }

            with(client.get("/metrics")) {
                status shouldBe HttpStatusCode.OK
            }
        }
}
