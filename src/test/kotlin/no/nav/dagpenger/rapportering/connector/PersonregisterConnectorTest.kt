package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
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
        statusCode: HttpStatusCode,
        responseBody: String = "",
    ) = PersonregisterConnector(
        personregisterUrl = personregisterUrl,
        tokenProvider = testTokenProvider,
        httpClient = createMockClient(statusCode, responseBody),
        actionTimer = actionTimer,
    )

    @Test
    fun `hentPersonstatus returnerer riktig overtattBekreftelse og ansvarligSystem`() {
        // True, ARENA
        var connector =
            personregisterConnector(
                HttpStatusCode.OK,
                """
                {
                  "ident": "$ident",
                  "status": "DAGPENGERBRUKER",
                  "overtattBekreftelse": true,
                  "ansvarligSystem": "ARENA" 
                }
                """.trimIndent(),
            )

        var response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response?.overtattBekreftelse shouldBe true
        response?.ansvarligSystem shouldBe AnsvarligSystem.ARENA

        // False, DP
        connector =
            personregisterConnector(
                HttpStatusCode.OK,
                """
                {
                  "ident": "$ident",
                  "status": "DAGPENGERBRUKER",
                  "overtattBekreftelse": false,
                  "ansvarligSystem": "DP" 
                }
                """.trimIndent(),
            )

        response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response?.overtattBekreftelse shouldBe false
        response?.ansvarligSystem shouldBe AnsvarligSystem.DP

        // Null, Null
        connector =
            personregisterConnector(
                HttpStatusCode.OK,
                """
                {
                  "ident": "$ident",
                  "status": "DAGPENGERBRUKER"
                }
                """.trimIndent(),
            )

        response =
            runBlocking {
                connector.hentPersonstatus(ident, subjectToken)
            }

        response?.overtattBekreftelse shouldBe false
        response?.ansvarligSystem shouldBe null
    }

    @Test
    fun `hentBrukerstatus returnerer IKKE_DAGPENGERBRUKER hvis 404 Not Found`() {
        val connector = personregisterConnector(HttpStatusCode.NotFound)

        val response =
            runBlocking {
                connector.hentBrukerstatus(ident, subjectToken)
            }

        response shouldBe Brukerstatus.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `hentBrukerstatus returnerer IKKE_DAGPENGERBRUKER hvis IKKE_DAGPENGERBRUKER`() {
        val connector =
            personregisterConnector(
                HttpStatusCode.OK,
                """
                {
                  "ident": "$ident",
                  "status": "IKKE_DAGPENGERBRUKER"
                }
                """.trimIndent(),
            )

        val response =
            runBlocking {
                connector.hentBrukerstatus(ident, subjectToken)
            }

        response shouldBe Brukerstatus.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `hentBrukerstatus returnerer DAGPENGERBRUKER hvis DAGPENGERBRUKER`() {
        val connector =
            personregisterConnector(
                HttpStatusCode.OK,
                """
                {
                  "ident": "$ident",
                  "status": "DAGPENGERBRUKER"
                }
                """.trimIndent(),
            )

        val response =
            runBlocking {
                connector.hentBrukerstatus(ident, subjectToken)
            }

        response shouldBe Brukerstatus.DAGPENGERBRUKER
    }

    @Test
    fun `oppdaterPersonstatus fungerer`() {
        val connector = personregisterConnector(HttpStatusCode.OK)

        runBlocking {
            connector.oppdaterPersonstatus(ident, subjectToken, LocalDate.now())
        }
    }
}
