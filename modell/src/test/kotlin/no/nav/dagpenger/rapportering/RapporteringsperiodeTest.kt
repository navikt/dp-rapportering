package no.nav.dagpenger.rapportering

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.helpers.TestData.godkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.lagRapporteringsperiode
import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Companion.erLåst
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

class RapporteringsperiodeTest {
    @Test
    fun `Kan hente ut gjeldende rapporteringsperiode`() {
        val periodeId = UUID.randomUUID()
        val rapporteringsperioder: List<Rapporteringsperiode> =
            listOf(
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
    fun `kan ikke godkjenne korrigeringer som ikke er faktisk endring`() {
        val innsendtPeriode =
            lagRapporteringsperiode(
                fom = 1.januar,
                tom = 14.januar,
                tilstand = Innsendt,
                aktiviteter =
                    listOf(
                        Aktivitet.Arbeid(6.januar, 3),
                    ),
            )
        innsendtPeriode.behandle(KorrigerPeriodeHendelse(testIdent, innsendtPeriode.rapporteringsperiodeId))
        val korrigertPeriode = innsendtPeriode.korrigertAv

        innsendtPeriode.tilstand shouldBe Innsendt
        korrigertPeriode.tilstand shouldBe TilUtfylling
        korrigertPeriode.korrigerer shouldBe innsendtPeriode

        korrigertPeriode.behandle(
            SlettAktivitetHendelse(
                testIdent,
                korrigertPeriode.rapporteringsperiodeId,
                korrigertPeriode.sisteAktivitet.uuid,
            ),
        )
        korrigertPeriode.behandle(nyAktivitetHendelse(korrigertPeriode.rapporteringsperiodeId, 6.januar))
        shouldThrow<IllegalStateException> {
            korrigertPeriode.behandle(godkjennPeriodeHendelse(korrigertPeriode.rapporteringsperiodeId))
        }
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

    @Test
    fun `Kan godkjenne og avgodkjenne en periode`() {
        val periode = lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = TilUtfylling)
        periode.behandle(nyAktivitetHendelse(periode.rapporteringsperiodeId, 5.januar))
        val avgodkjennHendelse =
            AvgodkjennPeriodeHendelse(
                ident = testIdent,
                rapporteringId = periode.rapporteringsperiodeId,
            )

        shouldThrow<IllegalStateException> {
            periode.behandle(avgodkjennHendelse)
        }

        periode.behandle(
            GodkjennPeriodeHendelse(
                ident = testIdent,
                rapporteringId = periode.rapporteringsperiodeId,
            ),
        )

        periode.aktiviteter.all { erLåst(it) } shouldBe true
        periode.tilstand shouldBe Godkjent

        periode.behandle(avgodkjennHendelse)

        periode.aktiviteter.all { erLåst(it) } shouldBe false
        periode.tilstand shouldBe TilUtfylling
    }

    @Test
    fun `Kan tidligst godkjenne en periode siste lørdag i perioden`() {
        val rapporteringsperiode = lagRapporteringsperiode(fom = 1.januar, tom = 14.januar, tilstand = TilUtfylling)
        val kanGodkjennesFra = rapporteringsperiode.kanGodkjennesFra

        val forTidligGodkjenningHendelse =
            godkjennPeriodeHendelse(
                rapporteringId = rapporteringsperiode.rapporteringsperiodeId,
                dato = kanGodkjennesFra.minusDays(1),
            )

        shouldThrow<GodkjenningExcpetion> { rapporteringsperiode.behandle(forTidligGodkjenningHendelse) }

        val godkjenningHendelse =
            godkjennPeriodeHendelse(
                rapporteringId = rapporteringsperiode.rapporteringsperiodeId,
                dato = kanGodkjennesFra,
            )

        shouldNotThrow<GodkjenningExcpetion> { rapporteringsperiode.behandle(godkjenningHendelse) }
    }

    private val Rapporteringsperiode.tilstand get() = TestVisitor(this).tilstand
    private val Rapporteringsperiode.korrigerer get() = TestVisitor(this).korrigerer!!
    private val Rapporteringsperiode.korrigertAv get() = TestVisitor(this).korrigertAv!!
    private val Rapporteringsperiode.aktiviteter get() = TestVisitor(this).aktiviteter

    private val Rapporteringsperiode.sisteAktivitet get() = TestVisitor(this).aktiviteter.last()

    private class TestVisitor(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
        lateinit var tilstand: Rapporteringsperiode.TilstandType
        var korrigerer: Rapporteringsperiode? = null
        var korrigertAv: Rapporteringsperiode? = null
        val aktiviteter = mutableListOf<Aktivitet>()

        init {
            rapporteringsperiode.accept(this)
        }

        override fun visit(
            rapporteringsperiode: Rapporteringsperiode,
            id: UUID,
            periode: ClosedRange<LocalDate>,
            tilstand: Rapporteringsperiode.TilstandType,
            beregnesEtter: LocalDate,
            korrigerer: Rapporteringsperiode?,
            korrigertAv: Rapporteringsperiode?,
        ) {
            this.tilstand = tilstand
            this.korrigerer = korrigerer
            this.korrigertAv = korrigertAv
        }

        override fun visit(
            aktivitet: Aktivitet,
            uuid: UUID,
            dato: LocalDate,
            tid: Duration,
            type: Aktivitet.AktivitetType,
            tilstand: Aktivitet.TilstandType,
        ) {
            this.aktiviteter.add(aktivitet)
        }
    }
}
