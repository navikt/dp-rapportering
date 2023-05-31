package no.nav.dagpenger.rapportering.hendelser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SøknadInnsendtHendelseTest {

    @Test
    fun `hjelpemetoder setter alltid fom dato til en mandag`() {
        val hendelse = SøknadInnsendtHendelse(UUID.randomUUID(), "123")
        assertEquals(DayOfWeek.MONDAY, hendelse.fraOgMed().dayOfWeek)

        val randomTirsdag = LocalDate.of(2023, 5, 30)
        assertFalse { randomTirsdag.erMandag() }

        val randomMandag = LocalDate.of(2023, 5, 29)
        assertTrue { randomMandag.erMandag() }

        assertEquals(randomMandag, finnForrigeMandag(fra = randomTirsdag))
    }
}
