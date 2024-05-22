package no.nav.dagpenger.rapportering.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.utils.januar
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class PeriodeTest {
    @Test
    fun `kan opprette periode når fra-og-med-datoen er 14 dager før til-og-med-datoen`() {
        val periode = Periode(1.januar, 14.januar)

        with(periode) {
            fraOgMed shouldBe 1.januar
            tilOgMed shouldBe 14.januar
        }
    }

    @Test
    fun `periode lengden er alltid 14 dager`() {
        val periode = Periode(1.januar, 14.januar)

        val periodeLengde = ChronoUnit.DAYS.between(periode.fraOgMed, periode.tilOgMed) + 1
        periodeLengde shouldBe 14
    }

    @Test
    fun `kan ikke opprette periode med mindre enn 14 dager`() {
        shouldThrow<IllegalArgumentException> {
            Periode(1.januar, 13.januar)
        }

        shouldThrow<IllegalArgumentException> {
            Periode(1.januar, 1.januar)
        }

        shouldThrow<IllegalArgumentException> {
            Periode(14.januar, 1.januar)
        }
    }
}
