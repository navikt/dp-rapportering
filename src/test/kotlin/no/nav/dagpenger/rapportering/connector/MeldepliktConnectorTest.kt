package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class MeldepliktConnectorTest {
    private fun meldepliktConnector(
        responseBody: String,
        statusCode: Int,
    ) = MeldepliktConnector(
        meldepliktUrl = "http://baseUrl",
        engine = createMockClient(statusCode, responseBody),
    )

    @Test
    fun `henter tom meldekortliste`() {
        val connector = meldepliktConnector("[]", 200)

        val response =
            runBlocking {
                connector.hentMeldekort("123")
            }

        response shouldBe "[]"
    }
}
