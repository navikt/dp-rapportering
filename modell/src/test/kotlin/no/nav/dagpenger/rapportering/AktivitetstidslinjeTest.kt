package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Syk
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
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
        // Lag et subset av den store tidslinjen
        val periode = 1.januar..14.januar
        val subset = tidslinje.forPeriode(periode.start, periode.endInclusive)
        // Subsettet skal alltid være like langt som perioden
        assertEquals(14, subset.size)
        // Legg til aktiviteter
        tidslinje.add(Arbeid(1.januar, 4.2))
        tidslinje.add(Arbeid(2.januar, 2.2))
        tidslinje.add(Arbeid(2.januar, 4.2))
        tidslinje.add(Syk(3.januar))
        tidslinje.add(Ferie(4.januar))

        assertEquals(15, subset.size)
        assertEquals(4, subset.dagerMedAktivitet)

        subset.forEach {
            assertTrue(it.dekkesAv(periode))
        }
        // Endringer gjort på den store tidslinjen reflekteres i subset
        val ferie = Ferie(5.januar)
        tidslinje.add(ferie)
        assertEquals(6, tidslinje.size)
        assertEquals(15, subset.size)

        // Endringer gjort på den store tidslinjen utenfor perioden reflekteres ikke i subset
        tidslinje.add(Ferie(22.januar))
        tidslinje.add(Ferie(23.januar))
        assertEquals(15, subset.size)
        assertEquals(8, tidslinje.size)
    }
}
