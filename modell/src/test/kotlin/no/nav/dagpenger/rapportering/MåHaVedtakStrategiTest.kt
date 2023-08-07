package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.februar
import no.nav.dagpenger.rapportering.helpers.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MåHaVedtakStrategiTest {
    @Test
    fun `Rapporteringersplikt som ikke er vedtak`() {
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(1.januar, IngenRapporteringsplikt(UUID.randomUUID(), 1.januar.atStartOfDay()))
            put(2.januar, RapporteringspliktSøknad(UUID.randomUUID(), 2.januar.atStartOfDay()))
        }

        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        val periode = 1.januar..14.januar
        rapporteringsplikt.alleSomDekkerPeriode(periode).size shouldBe 2
        strategi.skalBeregnes(periode) shouldBe false
    }

    @Test
    fun `Rapporteringsplikt som er vedtak i perioden`() {
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(1.januar, IngenRapporteringsplikt(UUID.randomUUID(), 1.januar.atStartOfDay()))
            put(2.januar, RapporteringspliktSøknad(UUID.randomUUID(), 2.januar.atStartOfDay()))
            put(3.januar, RapporteringspliktVedtak(UUID.randomUUID(), 3.januar.atStartOfDay()))
        }

        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        val periode = 1.januar..14.januar
        rapporteringsplikt.alleSomDekkerPeriode(periode).size shouldBe 3
        strategi.skalBeregnes(periode) shouldBe true
    }

    @Test
    fun `Rapporteringsplikt som er vedtak etter perioden`() {
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(1.januar, IngenRapporteringsplikt(UUID.randomUUID(), 1.januar.atStartOfDay()))
            put(2.januar, RapporteringspliktSøknad(UUID.randomUUID(), 2.januar.atStartOfDay()))
            put(3.januar, RapporteringspliktVedtak(UUID.randomUUID(), 3.januar.atStartOfDay()))
        }

        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        val periode = 1.februar..14.februar
        rapporteringsplikt.alleSomDekkerPeriode(periode).size shouldBe 1
        strategi.skalBeregnes(periode) shouldBe true
    }

    @Test
    fun `Rapporteringsplikt faller bort`() {
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(1.januar, IngenRapporteringsplikt(UUID.randomUUID(), 1.januar.atStartOfDay()))
            put(2.januar, RapporteringspliktSøknad(UUID.randomUUID(), 2.januar.atStartOfDay()))
            put(3.januar, RapporteringspliktVedtak(UUID.randomUUID(), 3.januar.atStartOfDay()))
            put(4.januar, IngenRapporteringsplikt(UUID.randomUUID(), 4.januar.atStartOfDay()))
        }

        val periode = 1.februar..14.februar
        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        rapporteringsplikt.alleSomDekkerPeriode(periode).size shouldBe 1
        strategi.skalBeregnes(periode) shouldBe false
    }

    @Test
    fun `Rapporteringsplikt faller bort i løpet av perioden`() {
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(1.januar, IngenRapporteringsplikt(UUID.randomUUID(), 1.januar.atStartOfDay()))
            put(2.januar, RapporteringspliktSøknad(UUID.randomUUID(), 2.januar.atStartOfDay()))
            put(3.januar, RapporteringspliktVedtak(UUID.randomUUID(), 3.januar.atStartOfDay()))
            put(4.februar, IngenRapporteringsplikt(UUID.randomUUID(), 4.februar.atStartOfDay()))
        }

        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        val periode = 1.februar..14.februar
        rapporteringsplikt.alleSomDekkerPeriode(periode).size shouldBe 2
        strategi.skalBeregnes(periode) shouldBe true
    }

    @Test
    fun `Perioder i eller etter vedtak skal sendes inn`() {
        val J14 = LocalDate.parse("2023-07-14")
        val J10 = LocalDate.parse("2023-07-10")
        val rapporteringsplikt = TemporalCollection<Rapporteringsplikt>().apply {
            put(J14, IngenRapporteringsplikt(UUID.randomUUID(), J14.atStartOfDay()))
            put(J14, RapporteringspliktSøknad(UUID.randomUUID(), J14.atStartOfDay()))
            put(J10, RapporteringspliktVedtak(UUID.randomUUID(), J10.atStartOfDay()))
        }

        val strategi = MåHaVedtakStrategi(rapporteringsplikt)
        strategi.skalBeregnes(LocalDate.parse("2023-07-24")..LocalDate.parse("2023-08-06")) shouldBe true
    }
}
