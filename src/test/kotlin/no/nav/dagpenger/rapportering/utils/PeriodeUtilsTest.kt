package no.nav.dagpenger.rapportering.utils

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodeUtilsTest {
    @Test
    fun `finnPeriodeKode finner riktig periodekode i overgangen mellom år`() {
        PeriodeUtils.finnPeriodeKode(30.desember(2019)) shouldBe "202001"
        PeriodeUtils.finnPeriodeKode(1.januar(2021)) shouldBe "202053"
        PeriodeUtils.finnPeriodeKode(30.desember(2024)) shouldBe "202501"
    }

    @Test
    fun `finnPeriodeKode starter ny uke på mandag`() {
        PeriodeUtils.finnPeriodeKode(17.mai(2020)) shouldBe "202020"
        PeriodeUtils.finnPeriodeKode(18.mai(2020)) shouldBe "202021"
    }

    @Test
    fun `finnKanSendesFra kan både legge til og trekke fra dager`() {
        PeriodeUtils.finnKanSendesFra(17.mai(2020), 5) shouldBe 22.mai(2020)
        PeriodeUtils.finnKanSendesFra(17.mai(2020), -5) shouldBe 12.mai(2020)
    }

    @Test
    fun `finnKanSendesFra trekker default fra 1 dag hvis justertInnsendingVerdi er null`() {
        PeriodeUtils.finnKanSendesFra(17.mai(2020), null) shouldBe 16.mai(2020)
    }

    @Test
    fun `kanSendesInn returnerer true hvis status er TilUtfylling og kanSendesFra er nå eller passert`() {
        PeriodeUtils.kanSendesInn(17.mai(2020), RapporteringsperiodeStatus.TilUtfylling) shouldBe true
        PeriodeUtils.kanSendesInn(LocalDate.now(), RapporteringsperiodeStatus.TilUtfylling) shouldBe true
    }

    @Test
    fun `kanSendesInn returnerer false hvis status ikke er TilUtfylling`() {
        PeriodeUtils.kanSendesInn(17.mai(2020), RapporteringsperiodeStatus.Endret) shouldBe false
        PeriodeUtils.kanSendesInn(17.mai(2020), RapporteringsperiodeStatus.Innsendt) shouldBe false
        PeriodeUtils.kanSendesInn(17.mai(2020), RapporteringsperiodeStatus.Ferdig) shouldBe false
        PeriodeUtils.kanSendesInn(17.mai(2020), RapporteringsperiodeStatus.Feilet) shouldBe false
    }

    @Test
    fun `kanSendesInn returnerer false hvis kanSendesFra er i fremtiden`() {
        PeriodeUtils.kanSendesInn(LocalDate.now().plusDays(1), RapporteringsperiodeStatus.TilUtfylling) shouldBe false
    }
}
