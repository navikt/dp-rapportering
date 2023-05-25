package no.nav.dagpenger.rapportering.tidslinje

import no.nav.dagpenger.rapportering.helpers.TestData.nyRapporteringHendelse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class AktivitetTest {
    @Test
    fun `aktivitet har tilstand`() {
        val aktivitet = Aktivitet.Arbeid(LocalDate.now(), 3)

        aktivitet.håndter(nyRapporteringHendelse())

        assertThrows<IllegalStateException> {
            aktivitet.håndter(nyRapporteringHendelse())
        }
    }
}
