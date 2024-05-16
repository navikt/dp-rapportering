package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.modeller.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.modeller.Periode
import no.nav.dagpenger.rapportering.modeller.Rapporteringsperiode
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
                ident shouldBe "123"
            }

            with(get(1)) {
                id shouldBe 1234L
                ident shouldBe "1234"
            }
        }
    }
}

val rapporteringsperiodeListe =
    listOf(
        Rapporteringsperiode(
            ident = "123",
            id = 123L,
            periode =
                Periode(
                    fra = LocalDate.now().minusWeeks(2),
                    til = LocalDate.now(),
                    kanSendesFra = LocalDate.now(),
                ),
            aktivitetstidslinje = Aktivitetstidslinje(),
            kanKorrigeres = true,
        ),
        Rapporteringsperiode(
            ident = "1234",
            id = 1234L,
            periode =
                Periode(
                    fra = LocalDate.now().minusWeeks(4),
                    til = LocalDate.now().minusWeeks(2),
                    kanSendesFra = LocalDate.now().minusWeeks(2),
                ),
            aktivitetstidslinje = Aktivitetstidslinje(),
            kanKorrigeres = false,
        ),
    )
