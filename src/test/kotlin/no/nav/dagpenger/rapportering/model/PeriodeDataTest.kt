package no.nav.dagpenger.rapportering.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PeriodeDataTest {
    @Test
    fun `meldt = true hvis periode startes tidligere`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().minusDays(1)),
                    null,
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe true
    }

    @Test
    fun `meldt = true hvis periode startes den dagen`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDate.now().atStartOfDay()),
                    null,
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe true
    }

    @Test
    fun `meldt = false hvis periode startes senere`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().plusDays(1)),
                    null,
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe false
    }

    @Test
    fun `meldt = true hvis periode startes tidligere og avsluttes den dagen`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().minusDays(1)),
                    metadataResponse(LocalDate.now().atTime(23, 59, 59)),
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe true
    }

    @Test
    fun `meldt = true hvis periode startes tidligere og avsluttes senere`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().minusDays(1)),
                    metadataResponse(LocalDateTime.now().plusDays(2)),
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe true
    }

    @Test
    fun `meldt = false hvis periode startes tidligere og avsluttes tidligere`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().minusDays(3)),
                    metadataResponse(LocalDateTime.now().minusDays(2)),
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe false
    }

    @Test
    fun `meldt = false hvis periode startes senere og avsluttes senere`() {
        val dager =
            listOf(
                Dag(
                    LocalDate.now(),
                    emptyList(),
                    0,
                ),
            )

        val perioder =
            listOf(
                ArbeidssøkerperiodeResponse(
                    UUID.randomUUID(),
                    metadataResponse(LocalDateTime.now().plusDays(1)),
                    metadataResponse(LocalDateTime.now().plusDays(2)),
                ),
            )

        val periodeDager = dager.toPeriodeDager(perioder)

        periodeDager.size shouldBe 1
        periodeDager[0].meldt shouldBe false
    }

    private fun metadataResponse(tidspunkt: LocalDateTime): MetadataResponse {
        return MetadataResponse(
            tidspunkt,
            BrukerResponse("", ""),
            "Kilde",
            "Årsak",
            null,
        )
    }
}
