package no.nav.dagpenger.rapportering.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNotEquals

class DatoUtilsTest {

    @Test
    fun `hjelpemetoder setter alltid fom dato til en mandag`() {
        val søndagUke1 = LocalDate.of(2023, 1, 8)
        val mandagUke2 = LocalDate.of(2023, 1, 9)
        assertNotEquals(mandagUke2, søndagUke1.finnFørsteMandagIUken())
        assertEquals(mandagUke2, mandagUke2.finnFørsteMandagIUken())

        val tirsdagUke2 = LocalDate.of(2023, 1, 10)
        assertEquals(mandagUke2, tirsdagUke2.finnFørsteMandagIUken())

        val skuddår = LocalDate.of(2024, 2, 29)
        val expected = LocalDate.of(2024, 2, 26)
        assertEquals(expected, skuddår.finnFørsteMandagIUken())
    }
}
