package no.nav.dagpenger.rapportering.model

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.PeriodeData.Kilde
import no.nav.dagpenger.rapportering.model.PeriodeData.OpprettetAv
import no.nav.dagpenger.rapportering.model.PeriodeData.PeriodeDag
import no.nav.dagpenger.rapportering.model.PeriodeData.Type
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

    @Test
    fun `kan konvertere til Rapporteringsperiode`() {
        val periode1 = Periode(LocalDate.now(), LocalDate.now().plusDays(13))
        val periode2 = Periode(LocalDate.now().minusDays(14), LocalDate.now().minusDays(1))

        val aktivitet1 =
            Aktivitet(
                UUID.randomUUID(),
                Aktivitet.AktivitetsType.Arbeid,
                "5.5",
            )
        val aktivitet2 =
            Aktivitet(
                UUID.randomUUID(),
                Aktivitet.AktivitetsType.Utdanning,
                "",
            )

        val periodeDataList =
            listOf(
                PeriodeData(
                    id = "1",
                    ident = "01020312345",
                    periode = periode1,
                    dager = emptyList(),
                    kanSendesFra = LocalDate.now(),
                    opprettetAv = OpprettetAv.Dagpenger,
                    kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312345"),
                    type = Type.Original,
                    status = "TilUtfylling",
                    innsendtTidspunkt = null,
                    originalMeldekortId = null,
                    bruttoBelop = null,
                    begrunnelse = null,
                    registrertArbeidssoker = null,
                    meldedato = null,
                ),
                PeriodeData(
                    id = "2",
                    ident = "01020312346",
                    periode = periode2,
                    dager =
                        listOf(
                            PeriodeDag(
                                LocalDate.now(),
                                listOf(aktivitet1),
                                0,
                            ),
                            PeriodeDag(
                                LocalDate.now().plusDays(1),
                                listOf(aktivitet2),
                                1,
                            ),
                        ),
                    kanSendesFra = LocalDate.now(),
                    opprettetAv = OpprettetAv.Arena,
                    kilde = Kilde(PeriodeData.Rolle.Bruker, "01020312346"),
                    type = Type.Korrigert,
                    status = "Endret",
                    innsendtTidspunkt = LocalDateTime.now(),
                    originalMeldekortId = "123456789",
                    bruttoBelop = 123.0,
                    begrunnelse = "Begrunnelse",
                    registrertArbeidssoker = true,
                    meldedato = LocalDate.now(),
                ),
            )

        val rapporteringsperioder = periodeDataList.toRapporteringsperioder()
        rapporteringsperioder.size shouldBe 2
        rapporteringsperioder[0].id shouldBe "1"
        rapporteringsperioder[0].type shouldBe "05"
        rapporteringsperioder[0].periode shouldBe periode1
        rapporteringsperioder[0].dager shouldBe emptyList()
        rapporteringsperioder[0].kanSendesFra shouldBe LocalDate.now()
        rapporteringsperioder[0].kanSendes shouldBe true
        rapporteringsperioder[0].kanEndres shouldBe true
        rapporteringsperioder[0].bruttoBelop shouldBe null
        rapporteringsperioder[0].begrunnelseEndring shouldBe null
        rapporteringsperioder[0].status shouldBe RapporteringsperiodeStatus.TilUtfylling
        rapporteringsperioder[0].mottattDato shouldBe null
        rapporteringsperioder[0].registrertArbeidssoker shouldBe null
        rapporteringsperioder[0].originalId shouldBe null
        rapporteringsperioder[0].rapporteringstype shouldBe null
        rapporteringsperioder[0].html shouldBe null
        rapporteringsperioder[1].id shouldBe "2"
        rapporteringsperioder[1].type shouldBe "10"
        rapporteringsperioder[1].periode shouldBe periode2
        rapporteringsperioder[1].dager.size shouldBe 2
        rapporteringsperioder[1].dager[0].dagIndex shouldBe 0
        rapporteringsperioder[1].dager[0].dato shouldBe LocalDate.now()
        rapporteringsperioder[1].dager[0].aktiviteter[0] shouldBe aktivitet1
        rapporteringsperioder[1].dager[1].dagIndex shouldBe 1
        rapporteringsperioder[1].dager[1].dato shouldBe LocalDate.now().plusDays(1)
        rapporteringsperioder[1].dager[1].aktiviteter[0] shouldBe aktivitet2
        rapporteringsperioder[1].kanSendesFra shouldBe LocalDate.now()
        rapporteringsperioder[1].kanSendes shouldBe false
        rapporteringsperioder[1].kanEndres shouldBe false
        rapporteringsperioder[1].bruttoBelop shouldBe 123.0
        rapporteringsperioder[1].begrunnelseEndring shouldBe "Begrunnelse"
        rapporteringsperioder[1].status shouldBe RapporteringsperiodeStatus.Endret
        rapporteringsperioder[1].mottattDato shouldBe LocalDate.now()
        rapporteringsperioder[1].registrertArbeidssoker shouldBe true
        rapporteringsperioder[1].originalId shouldBe "123456789"
        rapporteringsperioder[1].rapporteringstype shouldBe null
        rapporteringsperioder[1].html shouldBe null
    }

    private fun metadataResponse(tidspunkt: LocalDateTime): MetadataResponse =
        MetadataResponse(
            tidspunkt,
            BrukerResponse("", ""),
            "Kilde",
            "Årsak",
            null,
        )
}
