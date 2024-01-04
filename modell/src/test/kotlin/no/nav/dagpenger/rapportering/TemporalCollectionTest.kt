package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime

class TemporalCollectionTest {
    @Test
    fun `Henter ut elementer basert på LocalDateTime`() {
        val plikter = TemporalCollection<String>()
        val idag = LocalDateTime.now()
        val igår = idag.minusDays(1)

        plikter.put(igår, "Ingen")
        plikter.put(idag, "Søknad")

        plikter.get(idag) shouldBe "Søknad"
        plikter.get(igår) shouldBe "Ingen"
    }

    @Test
    fun `Henter ut elementer basert på LocalDate`() {
        val plikter = TemporalCollection<String>()
        val idag = LocalDate.now()
        val imorgen = idag.plusDays(1)

        plikter.put(idag, "Søknad")
        plikter.put(imorgen, "Vedtak")

        plikter.get(idag) shouldBe "Søknad"
        plikter.get(imorgen) shouldBe "Vedtak"
    }

    @Test
    fun `Får IllegalArgumentException når vi spør om noe som ikke finnes`() {
        val plikter = TemporalCollection<String>()
        val idag = LocalDate.now()
        val igår = idag.minusDays(1)

        plikter.put(idag, "Søknad")

        assertThrows<IllegalArgumentException> { plikter.get(igår) }
    }

    @Test
    fun `TemporalCollectionVisitor visiterer alle elementer`() {
        val plikter = TemporalCollection<String>()
        val visitor = TestTemporalCollectionVisitor<String>()
        val igår = LocalDateTime.now().minusDays(1)
        val idag = LocalDateTime.now()
        val imorgen = LocalDateTime.now().plusDays(1)

        plikter.put(igår, "Ingen")
        plikter.put(idag, "Søknad")
        plikter.put(imorgen, "Vedtak")
        plikter.accept(visitor)

        visitor.visitertDatoer shouldBe listOf(igår, idag, imorgen)
        visitor.visitertElementer shouldBe listOf("Ingen", "Søknad", "Vedtak")
    }
}

private class TestTemporalCollectionVisitor<R> : TemporalCollectionVisitor<R> {
    val visitertDatoer = mutableListOf<LocalDateTime>()
    val visitertElementer = mutableListOf<R>()

    override fun visit(
        at: LocalDateTime,
        item: R,
    ) {
        visitertDatoer.add(at)
        visitertElementer.add(item)
    }
}
