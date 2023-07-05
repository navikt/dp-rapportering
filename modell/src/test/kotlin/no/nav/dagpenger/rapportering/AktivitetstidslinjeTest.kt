package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Syk
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AktivitetstidslinjeTest {
    @Test
    fun `tidslinje holder på dager, fritak og aktiviteter`() {
        val periode = 1.januar..14.januar
        val tidslinje = Aktivitetstidslinje(periode)
        // Tidslinjen skal alltid ha like mange dager som perioden
        assertEquals(14, tidslinje.size)
        // Legg til dager uten rapporteringsplikt
        tidslinje.leggTilFritak(1.januar)
        tidslinje.leggTilFritak(2.januar)
        tidslinje.leggTilFritak(8.januar, 9.januar, 10.januar)
        // Kan ikke rapportere aktivitet på dager uten rapporteringsplikt
        assertThrows<IllegalStateException> {
            tidslinje.leggTilAktivitet(Arbeid(8.januar, "PT3H"))
        }
        // Legg til aktiviteter
        tidslinje.leggTilAktivitet(Arbeid(3.januar, "PT3H20M"))
        tidslinje.leggTilAktivitet(Arbeid(4.januar, "PT2H30M"))
        tidslinje.leggTilAktivitet(Syk(5.januar))
        tidslinje.leggTilAktivitet(Ferie(6.januar))

        assertEquals(14, tidslinje.size)
        assertEquals(4, tidslinje.dagerMedAktivitet)

        tidslinje.forEach {
            // Alle dager i tidslinja er dekket av perioden
            assertTrue(it.dekkesAv(periode))
        }
    }
}
