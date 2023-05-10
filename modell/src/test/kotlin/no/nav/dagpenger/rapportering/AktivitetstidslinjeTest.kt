package no.nav.dagpenger.rapportering

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AktivitetstidslinjeTest {
    @Test
    fun `tidslinje kan ha aktiviteter`() {
        val tidslinje = Aktivitetstidslinje()
        tidslinje.håndter(Aktivitet.Arbeid(LocalDate.now().minusDays(3), 4.2))
        tidslinje.håndter(Aktivitet.Arbeid(LocalDate.now().minusDays(3), 2.2))
        tidslinje.håndter(Aktivitet.Syk(LocalDate.now().minusDays(2)))
        tidslinje.håndter(Aktivitet.Ferie(LocalDate.now().minusDays(1)))

        assertEquals(tidslinje.antallAktiviteter(), 4)
        assertEquals(tidslinje.antallDager(), 3)
    }
}
