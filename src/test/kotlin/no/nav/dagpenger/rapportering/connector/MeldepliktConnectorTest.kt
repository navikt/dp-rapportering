package no.nav.dagpenger.rapportering.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Arbeid
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType.Syk
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Ferdig
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.Innsendt
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus.TilUtfylling
import no.nav.dagpenger.rapportering.utils.februar
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

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
    fun `henter tom rapporteringsperiodeliste`() {
        val connector = meldepliktConnector("[]", 200)

        val response =
            runBlocking {
                connector.hentRapporteringsperioder(ident, subjectToken)
            }

        response shouldBe emptyList()
    }

    @Test
    fun `henter rapporteringsperiodeliste med to elementer`() {
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
                dager.size shouldBe 14
                dager.first().dato shouldBe 1.januar
                dager.last().dato shouldBe 14.januar
                kanSendesFra shouldBe 13.januar
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
            }

            with(get(1)) {
                id shouldBe 456L
                periode.fraOgMed shouldBe 15.januar
                periode.tilOgMed shouldBe 28.januar
                dager.size shouldBe 14
                dager.first().dato shouldBe 15.januar
                dager.last().dato shouldBe 28.januar
                kanSendesFra shouldBe 27.januar
                status shouldBe TilUtfylling
                bruttoBelop shouldBe null
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

        shouldThrow<JsonConvertException> {
            runBlocking {
                connector.hentInnsendteRapporteringsperioder(ident, subjectToken)
            }
        }
    }

    @Test
    fun `henter tom liste for innsendte rapporteringsperioder`() {
        val connector = meldepliktConnector("[]", 200)

        val response =
            runBlocking {
                connector.hentInnsendteRapporteringsperioder(ident, subjectToken)
            }

        response shouldBe emptyList()
    }

    @Test
    fun `henter liste med innsedte rapporteringsperioder med tre elementer`() {
        val rapporteringsperiode1 =
            rapporteringsperiodeFor(
                id = 123L,
                fraOgMed = 1.januar,
                tilOgMed = 14.januar,
                dager = aktivitetsdagerlisteFor(startDato = 1.januar, aktivitet = aktivitetslisteFor(Arbeid, 5.5)),
                kanSendesFra = 13.januar,
                status = Innsendt,
            )
        val rapporteringsperiode2 =
            rapporteringsperiodeFor(
                id = 456L,
                fraOgMed = 15.januar,
                tilOgMed = 28.januar,
                dager = aktivitetsdagerlisteFor(startDato = 15.januar, aktivitet = aktivitetslisteFor(Arbeid, .5)),
                kanSendesFra = 27.januar,
                status = Ferdig,
                bruttoBelop = "1000",
            )
        val rapporteringsperiode3 =
            rapporteringsperiodeFor(
                id = 456L,
                fraOgMed = 29.januar,
                tilOgMed = 11.februar,
                dager = aktivitetsdagerlisteFor(startDato = 29.januar, aktivitet = aktivitetslisteFor(Syk)),
                kanSendesFra = 10.februar,
                status = Ferdig,
                bruttoBelop = "1000",
            )

        val rapporteringsperioder = "[$rapporteringsperiode1, $rapporteringsperiode2, $rapporteringsperiode3]"

        val connector = meldepliktConnector(rapporteringsperioder, 200)

        val response =
            runBlocking {
                connector.hentRapporteringsperioder(ident, subjectToken)
            }

        with(response) {
            size shouldBe 3

            with(get(0)) {
                id shouldBe 123L
                periode.fraOgMed shouldBe 1.januar
                periode.tilOgMed shouldBe 14.januar
                dager.size shouldBe 14
                dager.first().dato shouldBe 1.januar
                dager.last().dato shouldBe 14.januar
                dager.first().aktiviteter.first().type shouldBe Arbeid
                dager.first().aktiviteter.first().timer shouldBe "PT5H30M"
                kanSendesFra shouldBe 13.januar
                status shouldBe Innsendt
                bruttoBelop shouldBe null
            }

            with(get(1)) {
                id shouldBe 456L
                periode.fraOgMed shouldBe 15.januar
                periode.tilOgMed shouldBe 28.januar
                dager.size shouldBe 14
                dager.first().dato shouldBe 15.januar
                dager.last().dato shouldBe 28.januar
                dager.first().aktiviteter.first().type shouldBe Arbeid
                dager.first().aktiviteter.first().timer.toString() shouldBe "PT30M"
                kanSendesFra shouldBe 27.januar
                status shouldBe Ferdig
                bruttoBelop shouldBe 1000.0
            }
            with(get(2)) {
                id shouldBe 456L
                periode.fraOgMed shouldBe 29.januar
                periode.tilOgMed shouldBe 11.februar
                dager.size shouldBe 14
                dager.first().aktiviteter.first().type shouldBe Syk
                dager.first().aktiviteter.first().timer shouldBe null
                kanSendesFra shouldBe 10.februar
            }
        }
    }

    @Test
    fun `henter korringeringId`() {
        val id = 1806985352L
        val connector = meldepliktConnector(id.toString(), 200)

        val response =
            runBlocking {
                connector.hentKorrigeringId(id, subjectToken)
            }

        response shouldBe id
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
    dager: String = aktivitetsdagerlisteFor(startDato = fraOgMed),
    kanSendesFra: LocalDate = LocalDate.now(),
    kanSendes: Boolean = true,
    kanKorrigeres: Boolean = true,
    status: RapporteringsperiodeStatus = TilUtfylling,
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

fun aktivitetsdagerlisteFor(
    startDato: LocalDate = LocalDate.now().minusWeeks(2),
    aktivitet: String? = null,
) = //language=JSON
    """
    [
        {
            "dato": "$startDato",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 0
        },
        {
            "dato": "${startDato.plusDays(1)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 1
        },
        {
            "dato": "${startDato.plusDays(2)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 2
        },
        {
            "dato": "${startDato.plusDays(3)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 3
        },
        {
            "dato": "${startDato.plusDays(4)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 4
        },
        {
            "dato": "${startDato.plusDays(5)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 5
        },
        {
            "dato": "${startDato.plusDays(6)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 6
        },
        {
            "dato": "${startDato.plusDays(7)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 7
        },
        {
            "dato": "${startDato.plusDays(8)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 8
        },
        {
            "dato": "${startDato.plusDays(9)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 9
        },
        {
            "dato": "${startDato.plusDays(10)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 10
        },
        {
            "dato": "${startDato.plusDays(11)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 11
        },
        {
            "dato": "${startDato.plusDays(12)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 12
        },
        {
            "dato": "${startDato.plusDays(13)}",
            "aktiviteter": ${aktivitet ?: "[]"},
            "dagIndex": 13
        }
    ]
    """.trimIndent()

fun aktivitetslisteFor(
    type: AktivitetsType,
    timer: Double? = null,
) = //language=JSON
    """
    [
      {
        "uuid": "${UUID.randomUUID()}",
        "type": "$type",
        "timer": $timer
      }
    ]
    """.trimIndent()
