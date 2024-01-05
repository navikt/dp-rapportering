package no.nav.dagpenger.rapportering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Syk
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import org.junit.jupiter.api.Test

class AktivitetstidslinjeTest {
    @Test
    fun `tidslinje har riktig antall dager`() {
        val periode = 1.januar..14.januar

        val tidslinje = Aktivitetstidslinje(periode)

        tidslinje.size shouldBe 14
        tidslinje.dagerMedAktivitet shouldBe 0
    }

    @Test
    fun `kan ikke rapportere aktivitet på dager uten rapporteringsplikt`() {
        val periode = 1.januar..14.januar
        val tidslinje = Aktivitetstidslinje(periode)
        val fritaksDager = listOf(1.januar, 2.januar, 3.januar)

        tidslinje.leggTilFritak(*fritaksDager.toTypedArray())

        fritaksDager.forEach {
            shouldThrow<IllegalStateException> { tidslinje.leggTilAktivitet(Arbeid(it, "PT3H")) }
        }
    }

    @Test
    fun `legge til aktiviteter`() {
        val periode = 1.januar..14.januar
        val tidslinje = Aktivitetstidslinje(periode)

        tidslinje.apply {
            leggTilAktivitet(Arbeid(3.januar, "PT3H20M"))
            leggTilAktivitet(Arbeid(4.januar, "PT2H30M"))
            leggTilAktivitet(Syk(5.januar))
            leggTilAktivitet(Ferie(6.januar))
        }

        tidslinje.dagerMedAktivitet shouldBe 4
        tidslinje.size shouldBe 14
    }

    @Test
    fun `alle dager i tidslinja er dekket av perioden`() {
        val periode = 1.januar..14.januar
        val tidslinje = Aktivitetstidslinje(periode)

        tidslinje.apply {
            leggTilFritak(1.januar)
            leggTilAktivitet(Arbeid(2.januar, "PT3H20M"))
            leggTilAktivitet(Syk(5.januar))
            leggTilAktivitet(Ferie(6.januar))
        }

        tidslinje.all { it.dekkesAv(periode) } shouldBe true
    }
}
