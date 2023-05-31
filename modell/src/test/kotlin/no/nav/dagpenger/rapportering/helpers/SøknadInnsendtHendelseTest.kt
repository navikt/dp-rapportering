package no.nav.dagpenger.rapportering.helpers

import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.finnFørsteMandagIUken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertNotEquals

class SøknadInnsendtHendelseTest {

    @Test
    fun `hjelpemetoder setter alltid fom dato til en mandag`() {
        val hendelse = SøknadInnsendtHendelse(UUID.randomUUID(), "123")
        assertEquals(DayOfWeek.MONDAY, hendelse.fraOgMed().dayOfWeek)

        val søndagUke1 = LocalDate.of(2023, 1, 8)
        val mandagUke2 = LocalDate.of(2023, 1, 9)
        val tirsdagUke2 = LocalDate.of(2023, 1, 10)

        assertNotEquals(mandagUke2, søndagUke1.finnFørsteMandagIUken())
        assertEquals(mandagUke2, mandagUke2.finnFørsteMandagIUken())
        assertEquals(mandagUke2, tirsdagUke2.finnFørsteMandagIUken())

        val skuddår = LocalDate.of(2024, 2, 29)
        val expected = LocalDate.of(2024, 2, 26)
        assertEquals(expected, skuddår.finnFørsteMandagIUken())
    }
}
