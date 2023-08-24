package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TemporalCollectionTest {
    @Test
    fun `Henter ut element på riktig dato`() {
        val plikter = TemporalCollection<String>()
        plikter.put(LocalDate.now(), "foobar")

        plikter.get(LocalDate.now()) shouldBe "foobar"
    }
}
