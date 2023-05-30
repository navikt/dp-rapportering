package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Syk
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AktivitetstidslinjeTest {
    @Test
    fun `tidslinje kan ha aktiviteter`() {
        val tidslinje = Aktivitetstidslinje()
        tidslinje.add(Arbeid(LocalDate.now().minusDays(3), 4.2))
        tidslinje.add(Arbeid(LocalDate.now().minusDays(3), 2.2))
        tidslinje.add(Syk(LocalDate.now().minusDays(2)))
        tidslinje.add(Ferie(LocalDate.now().minusDays(1)))

        assertEquals(4, tidslinje.size)
        assertEquals(3, tidslinje.dagerMedAktivitet)
    }

    @Test
    fun `tidslinjer kan ha subset`() {
        val tidslinje = Aktivitetstidslinje()
        tidslinje.add(Arbeid(LocalDate.now().minusDays(3), 4.2))
        tidslinje.add(Arbeid(LocalDate.now().minusDays(3), 2.2))
        tidslinje.add(Syk(LocalDate.now().minusDays(2)))
        tidslinje.add(Ferie(LocalDate.now().minusDays(1)))

        // Lag et subset av den store tidslinjen
        val periode = LocalDate.now().minusDays(2)..LocalDate.now()
        val subset = tidslinje.forPeriode(periode.start, periode.endInclusive)

        assertEquals(2, subset.size)
        assertEquals(2, subset.dagerMedAktivitet)

        subset.forEach {
            assertTrue(it.dekkesAv(periode))
        }

        // Endringer gjort på den store tidslinjen reflekteres i subset
        val ferie = Ferie(LocalDate.now())
        tidslinje.add(ferie)
        assertEquals(5, tidslinje.size)
        assertEquals(3, subset.size)

        // Alt er by reference så aktiviteter oppdateres i den store tidslinjen
        assertSame(subset.last(), ferie)
        assertSame(subset.last(), tidslinje.last())

        // Endringer gjort på den store tidslinjen utenfor perioden reflekteres ikke i subset
        tidslinje.add(Ferie(LocalDate.now().minusDays(10)))
        tidslinje.add(Ferie(LocalDate.now().plusDays(10)))
        assertEquals(3, subset.size)
        assertEquals(7, tidslinje.size)
    }
}
