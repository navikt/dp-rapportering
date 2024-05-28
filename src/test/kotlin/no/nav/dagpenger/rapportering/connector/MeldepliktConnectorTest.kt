package no.nav.dagpenger.rapportering.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MeldepliktConnectorTest {
    private val testTokenProvider: (token: String) -> String = { _ -> "testToken" }
    private val meldepliktUrl = "http://meldepliktAdapterUrl"
    private val subjectToken = "gylidg_token"
    private val ident = "12345678903"
    private val rapporteringId = "1806478069"

    private fun meldepliktConnector(
        responseBody: String,
        statusCode: Int,
    ) = MeldepliktConnector(
        meldepliktUrl = meldepliktUrl,
        tokenProvider = testTokenProvider,
        engine = createMockClient(statusCode, responseBody),
    )

    @Test
    fun `henter tom meldekortliste`() {
        val connector = meldepliktConnector("[]", 200)

        val response =
            runBlocking {
                connector.hentRapporteringsperioder(ident, subjectToken)
            }

        response shouldBe emptyList()
    }

    @Test
    fun `henter meldekortliste med to elementer`() {
        val rapporteringsperiode1 =
            rapporteringsperiodeFor(id = 123L, fraOgMed = 1.januar, tilOgMed = 14.januar, kanSendesFra = 13.januar)
        val rapporteringsperiode2 =
            rapporteringsperiodeFor(id = 456L, fraOgMed = 15.januar, tilOgMed = 28.januar, kanSendesFra = 27.januar)

        val rapporteringsperioder = "[$rapporteringsperiode1, $rapporteringsperiode2]"

        val connector = meldepliktConnector(rapporteringsperioder, 200)

        val response =
            runBlocking {
                connector.hentRapporteringsperioder(ident, subjectToken)
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

    @Test
    fun `feiler ved ugyldig periode`() {
        val rapporteringsperioder =
            rapporteringsperiodeFor(id = 123L, fraOgMed = 1.januar, tilOgMed = 10.januar)
        val connector = meldepliktConnector("[$rapporteringsperioder]", 200)

        shouldThrow<JsonConvertException> {
            runBlocking {
                connector.hentRapporteringsperioder(ident, subjectToken)
            }
        }
    }

    @Test
    fun `henter tom aktivitetsdagerliste`() {
        val connector = meldepliktConnector("[]", 200)

        val response =
            runBlocking {
                connector.hentAktivitetsdager(rapporteringId, subjectToken)
            }

        response shouldBe emptyList()
    }

    @Test
    fun `henter aktivitetsdagerliste med 14 elementer`() {
        val connector = meldepliktConnector(aktivitetsdagerlisteFor(), 200)

        val response =
            runBlocking {
                connector.hentAktivitetsdager(rapporteringId, subjectToken)
            }

        with(response) {
            size shouldBe 14
            first().dato shouldBe LocalDate.now().minusWeeks(2)
            last().dato shouldBe LocalDate.now().minusDays(1)
        }
    }
}

fun rapporteringsperiodeFor(
    id: Long = 123L,
    fraOgMed: LocalDate = LocalDate.now().minusWeeks(2),
    tilOgMed: LocalDate = LocalDate.now(),
    dager: String = aktivitetsdagerlisteFor(fraOgMed),
    kanSendesFra: LocalDate = LocalDate.now(),
    kanSendes: Boolean = true,
    kanKorrigeres: Boolean = true,
    status: RapporteringsperiodeStatus = RapporteringsperiodeStatus.TilUtfylling,
    bruttoBelop: String? = null,
) = //language=JSON
    """
    {
      "id": $id,
      "periode": {
        "fraOgMed": "$fraOgMed",
        "tilOgMed": "$tilOgMed"
      },
      "dager": $dager,
      "kanSendesFra": "$kanSendesFra",
      "kanSendes": $kanSendes,
      "kanKorrigeres": $kanKorrigeres,
      "bruttoBelop": $bruttoBelop,
      "status": "${status.name}"
    }
    """.trimIndent()

fun aktivitetsdagerlisteFor(startDato: LocalDate = LocalDate.now().minusWeeks(2)) =
    //language=JSON
    """
    [
        {
            "dato": "$startDato",
            "aktiviteter": [],
            "dagIndex": 0
        },
        {
            "dato": "${startDato.plusDays(1)}",
            "aktiviteter": [],
            "dagIndex": 1
        },
        {
            "dato": "${startDato.plusDays(2)}",
            "aktiviteter": [],
            "dagIndex": 2
        },
        {
            "dato": "${startDato.plusDays(3)}",
            "aktiviteter": [],
            "dagIndex": 3
        },
        {
            "dato": "${startDato.plusDays(4)}",
            "aktiviteter": [],
            "dagIndex": 4
        },
        {
            "dato": "${startDato.plusDays(5)}",
            "aktiviteter": [],
            "dagIndex": 5
        },
        {
            "dato": "${startDato.plusDays(6)}",
            "aktiviteter": [],
            "dagIndex": 6
        },
        {
            "dato": "${startDato.plusDays(7)}",
            "aktiviteter": [],
            "dagIndex": 7
        },
        {
            "dato": "${startDato.plusDays(8)}",
            "aktiviteter": [],
            "dagIndex": 8
        },
        {
            "dato": "${startDato.plusDays(9)}",
            "aktiviteter": [],
            "dagIndex": 9
        },
        {
            "dato": "${startDato.plusDays(10)}",
            "aktiviteter": [],
            "dagIndex": 10
        },
        {
            "dato": "${startDato.plusDays(11)}",
            "aktiviteter": [],
            "dagIndex": 11
        },
        {
            "dato": "${startDato.plusDays(12)}",
            "aktiviteter": [],
            "dagIndex": 12
        },
        {
            "dato": "${startDato.plusDays(13)}",
            "aktiviteter": [],
            "dagIndex": 13
        }
    ]
    """.trimIndent()
