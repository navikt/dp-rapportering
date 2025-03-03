package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.api.ApiTestSetup.Companion.setEnvConfig
import no.nav.dagpenger.rapportering.utils.MetricsTestUtil.actionTimer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonregisterConnectorTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val personregisterUrl = "http://personregisterUrl"
    private val subjectToken = "gylidg_token"
    private val ident = "12345678903"

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            setEnvConfig()
        }
    }

    private fun personregisterConnector(
        responseBody: String,
        statusCode: Int,
    ) = PersonregisterConnector(
        personregisterUrl = personregisterUrl,
        tokenProvider = testTokenProvider,
        httpClient = createMockClient(statusCode, responseBody),
        actionTimer = actionTimer,
    )

    @Test
    fun `hentPersonstatus returnerer IKKE_DAGPENGERBRUKER hvis 404 Not Found`() {
        val connector = personregisterConnector("", 404)

        val response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response shouldBe Personstatus.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `hentPersonstatus returnerer IKKE_DAGPENGERBRUKER hvis IKKE_DAGPENGERBRUKER`() {
        val connector =
            personregisterConnector(
                """
                {
                  "ident": "$ident",
                  "status": "IKKE_DAGPENGERBRUKER"
                }
                """.trimIndent(),
                200,
            )

        val response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response shouldBe Personstatus.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `hentPersonstatus returnerer DAGPENGERBRUKER hvis DAGPENGERBRUKER`() {
        val connector =
            personregisterConnector(
                """
                {
                  "ident": "$ident",
                  "status": "DAGPENGERBRUKER"
                }
                """.trimIndent(),
                200,
            )

        val response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response shouldBe Personstatus.DAGPENGERBRUKER
    }

    @Test
    fun `oppdaterPersonstatus fungerer`() {
        val connector = personregisterConnector("", 200)

        runBlocking {
            connector.oppdaterPersonstatus(ident, subjectToken, LocalDate.now())
        }
    }
}
