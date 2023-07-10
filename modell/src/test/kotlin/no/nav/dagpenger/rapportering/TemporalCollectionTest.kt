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

        plikter.any(3.januar..5.januar) { it != "bar" } shouldBe true
        plikter.any(3.januar..5.januar) { it == "foo" } shouldBe true
        plikter.any(3.januar..5.januar) { it == "foobar" } shouldBe true
        plikter.any(3.januar..5.januar) { it != "barfoo" } shouldBe true
    }
}
