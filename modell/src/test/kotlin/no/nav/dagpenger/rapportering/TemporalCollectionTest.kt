package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.helpers.januar
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TemporalCollectionTest {
    @Test
    fun `Henter ut element på riktig dato`() {
        val plikter = TemporalCollection<String>()
        plikter.put(LocalDate.now(), "foobar")

        plikter.get(LocalDate.now()) shouldBe "foobar"
    }

    @Test
    fun `Kan sjekke om et element i en periode `() {
        val plikter = TemporalCollection<String>()
        plikter.put(1.januar, "bar")
        plikter.put(3.januar, "foo")
        plikter.put(5.januar, "foobar")
        plikter.put(8.januar, "barfoo")

        plikter.alleSomDekkerPeriode(3.januar..5.januar).size shouldBe 2
        plikter.alleSomDekkerPeriode(3.januar..5.januar).size shouldBe 2
        plikter.alleSomDekkerPeriode(3.januar..5.januar).size shouldBe 2
        plikter.alleSomDekkerPeriode(3.januar..5.januar).size shouldBe 2
    }
}
