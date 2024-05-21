package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldepliktConnectorTest {
    private fun meldepliktConnector(
        responseBody: Any,
        statusCode: Int,
    ) = MeldepliktConnector(
        meldepliktUrl = "http://baseUrl",
        engine = createMockClient(statusCode, responseBody),
    )

    @Test
    fun `henter tom meldekortliste`() {
        val connector = meldepliktConnector(emptyList<Rapporteringsperiode>(), 200)

        val response =
            runBlocking {
                connector.hentMeldekort("123")
            }

        response shouldBe emptyList()
    }

    @Test
    fun `henter meldekortliste med to elementer`() {
        val connector = meldepliktConnector(rapporteringsperiodeListe, 200)

        val response =
            runBlocking {
                connector.hentMeldekort("123")
            }

        with(response) {
            size shouldBe 2

            with(get(0)) {
                id shouldBe 123L
            }

            with(get(1)) {
                id shouldBe 1234L
            }
        }
    }
}

val rapporteringsperiodeListe =
    listOf(
        Rapporteringsperiode(
            id = 123L,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(2),
                    tilOgMed = LocalDate.now(),
                ),
            dager = emptyList(),
            kanSendesFra = LocalDate.now(),
            kanSendes = true,
            kanKorrigeres = true,
        ),
        Rapporteringsperiode(
            id = 1234L,
            periode =
                Periode(
                    fraOgMed = LocalDate.now().minusWeeks(4),
                    tilOgMed = LocalDate.now().minusWeeks(2),
                ),
            dager = emptyList(),
            kanSendesFra = LocalDate.now().minusWeeks(2),
            kanSendes = true,
            kanKorrigeres = false,
        ),
    )
