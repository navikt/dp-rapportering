package no.nav.dagpenger.rapportering.connector

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.utils.januar
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
        val rapporteringsperiode1 = rapporteringsperiodeFor(id = 123L, fraOgMed = 1.januar, tilOgMed = 14.januar, kanSendesFra = 13.januar)
        val rapporteringsperiode2 = rapporteringsperiodeFor(id = 456L, fraOgMed = 15.januar, tilOgMed = 28.januar, kanSendesFra = 27.januar)
        val rapporteringsperioder = listOf(rapporteringsperiode1, rapporteringsperiode2)

        val connector = meldepliktConnector(rapporteringsperioder, 200)

        val response =
            runBlocking {
                connector.hentMeldekort("123")
            }

        with(response) {
            size shouldBe 2

            with(get(0)) {
                id shouldBe 123L
                periode.fraOgMed shouldBe 1.januar
                periode.tilOgMed shouldBe 14.januar
                kanSendesFra shouldBe 13.januar
            }

            with(get(1)) {
                id shouldBe 456L
                periode.fraOgMed shouldBe 15.januar
                periode.tilOgMed shouldBe 28.januar
                kanSendesFra shouldBe 27.januar
            }
        }
    }
}

fun rapporteringsperiodeFor(
    id: Long = 123L,
    fraOgMed: LocalDate = LocalDate.now().minusWeeks(2),
    tilOgMed: LocalDate = LocalDate.now(),
    dager: List<Dag> = emptyList(),
    kanSendesFra: LocalDate = LocalDate.now(),
    kanSendes: Boolean = true,
    kanKorrigeres: Boolean = true,
) = Rapporteringsperiode(
    id = id,
    periode = Periode(fraOgMed, tilOgMed),
    dager = dager,
    kanSendesFra = kanSendesFra,
    kanSendes = kanSendes,
    kanKorrigeres = kanKorrigeres,
)
