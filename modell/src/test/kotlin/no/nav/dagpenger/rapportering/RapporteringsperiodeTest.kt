package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.helpers.TestData.godkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RapporteringsperiodeTest {
    @Test
    fun `Kan hente ut gjeldende rapporteringsperiode`() {
        val periodeId = UUID.randomUUID()
        val rapporteringsperioder: List<Rapporteringsperiode> = listOf(
            lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = Godkjent),
            lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = TilUtfylling, id = periodeId),
        )

        rapporteringsperioder.hentGjeldende(dato = 7.januar)!!.rapporteringsperiodeId shouldBe periodeId
        rapporteringsperioder.hentGjeldende(dato = 15.januar) shouldBe null
    }

    @Test
    fun `kan korrigere innsendt periode`() {
        val innsendtPeriode = lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = Innsendt)
        val korrigertPeriode = innsendtPeriode.lagKorrigering()

        innsendtPeriode.tilstand shouldBe Innsendt
        korrigertPeriode.tilstand shouldBe TilUtfylling
        korrigertPeriode.korrigerer shouldBe innsendtPeriode

        korrigertPeriode.behandle(nyAktivitetHendelse(korrigertPeriode.rapporteringsperiodeId, 6.januar))
        korrigertPeriode.behandle(godkjennPeriodeHendelse(korrigertPeriode.rapporteringsperiodeId))
        korrigertPeriode.tilstand shouldBe Godkjent
    }

    private val Rapporteringsperiode.tilstand get() = TestVisitor(this).tilstand

    private class TestVisitor(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
        lateinit var tilstand: Rapporteringsperiode.TilstandType

        init {
            rapporteringsperiode.accept(this)
        }

        override fun visit(
            rapporteringsperiode: Rapporteringsperiode,
            id: UUID,
            periode: ClosedRange<LocalDate>,
            tilstand: Rapporteringsperiode.TilstandType,
            rapporteringsfrist: LocalDate,
        ) {
            this.tilstand = tilstand
        }
    }

    private fun lagRapporteringsperiode(
        fom: LocalDate,
        tom: LocalDate,
        tilstand: Rapporteringsperiode.TilstandType,
        id: UUID = UUID.randomUUID(),
    ): Rapporteringsperiode {
        return Rapporteringsperiode.rehydrer(
            rapporteringsperiodeId = id,
            rapporteringsfrist = tom,
            fraOgMed = fom,
            tilOgMed = tom,
            tilstand = tilstand,
            opprettet = LocalDateTime.now(),
            tidslinje = Aktivitetstidslinje(fom..tom),
        )
    }
}
