package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class MeldepliktConnectorTest {
    private val meldepliktUrl = "http://meldepliktUrl"

    @Test
    fun `meldeplikt API svarer med tom liste`() {
        runBlocking {
            val mockEngine = createMockClient(200, "[]")
            val meldepliktConnector = MeldepliktConnector(meldepliktUrl, mockEngine)

            val response = meldepliktConnector.hentMeldekort("123")
            response shouldBe "[]"
        }
    }
}
