package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.helpers.TestData.godkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
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
        innsendtPeriode.behandle(KorrigerPeriodeHendelse(testIdent, innsendtPeriode.rapporteringsperiodeId))
        val korrigertPeriode = innsendtPeriode.korrigertAv

        innsendtPeriode.tilstand shouldBe Innsendt
        korrigertPeriode.tilstand shouldBe TilUtfylling
        korrigertPeriode.korrigerer shouldBe innsendtPeriode

        korrigertPeriode.behandle(nyAktivitetHendelse(korrigertPeriode.rapporteringsperiodeId, 6.januar))
        korrigertPeriode.behandle(godkjennPeriodeHendelse(korrigertPeriode.rapporteringsperiodeId))
        korrigertPeriode.tilstand shouldBe Godkjent
    }

    @Test
    fun `kan erstatte påbegynt korrigering`() {
        val innsendtPeriode = lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = Innsendt)

        // Opprett en korrigering og verifisert at det er korrigeringen som kommer tilbake
        innsendtPeriode.behandle(KorrigerPeriodeHendelse(testIdent, innsendtPeriode.rapporteringsperiodeId))
        val korrigertPeriode1 = innsendtPeriode.korrigertAv
        korrigertPeriode1.tilstand shouldBe TilUtfylling
        innsendtPeriode.finnSisteKorrigering() shouldBe korrigertPeriode1

        // Opprett ny korrigering som erstatter forrige påbegynte korrigering
        innsendtPeriode.behandle(KorrigerPeriodeHendelse(testIdent, innsendtPeriode.rapporteringsperiodeId))
        val korrigertPeriode2 = innsendtPeriode.korrigertAv
        innsendtPeriode.finnSisteKorrigering() shouldBe korrigertPeriode2
        korrigertPeriode2 shouldNotBe korrigertPeriode1
        korrigertPeriode2.korrigerer shouldBe innsendtPeriode

        korrigertPeriode2.behandle(nyAktivitetHendelse(korrigertPeriode2.rapporteringsperiodeId, 6.januar))
        listOf(innsendtPeriode).hentGjeldende(5.januar) shouldBe null

        korrigertPeriode2.behandle(godkjennPeriodeHendelse(korrigertPeriode2.rapporteringsperiodeId))
        korrigertPeriode2.tilstand shouldBe Godkjent
    }

    private val Rapporteringsperiode.tilstand get() = TestVisitor(this).tilstand
    private val Rapporteringsperiode.korrigerer get() = TestVisitor(this).korrigerer!!
    private val Rapporteringsperiode.korrigertAv get() = TestVisitor(this).korrigertAv!!

    private class TestVisitor(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
        lateinit var tilstand: Rapporteringsperiode.TilstandType
        var korrigerer: Rapporteringsperiode? = null
        var korrigertAv: Rapporteringsperiode? = null

        init {
            rapporteringsperiode.accept(this)
        }

        override fun visit(
            rapporteringsperiode: Rapporteringsperiode,
            id: UUID,
            periode: ClosedRange<LocalDate>,
            tilstand: Rapporteringsperiode.TilstandType,
            rapporteringsfrist: LocalDate,
            korrigerer: Rapporteringsperiode?,
            korrigertAv: Rapporteringsperiode?,
        ) {
            this.tilstand = tilstand
            this.korrigerer = korrigerer
            this.korrigertAv = korrigertAv
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
            korrigerer = null,
            korrigertAv = null,
        )
    }
}
