package no.nav.dagpenger.rapportering.model

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.api.rapporteringsperiodeFor
import org.junit.jupiter.api.Test
import java.util.UUID

class RapporteringsperiodeTest {
    @Test
    fun `Arbeidet = true hvis bruker jobbet`() {
        val rapporteringsperiode =
            rapporteringsperiodeFor(
                aktivitet = Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Arbeid, "PT7H30M"),
            )

        rapporteringsperiode.arbeidet() shouldBe true
    }

    @Test
    fun `Arbeidet = false true hvis bruker ikke jobbet`() {
        val rapporteringsperiode =
            rapporteringsperiodeFor(
                aktivitet = Aktivitet(UUID.randomUUID(), Aktivitet.AktivitetsType.Fravaer, ""),
            )

        rapporteringsperiode.arbeidet() shouldBe false
    }
}
