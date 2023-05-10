package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyRapporteringHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyRapporteringsperiodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.søknadInnsendtHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.TestData.testPerson
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

class PersonTest {
    @Test
    fun `person håndterer hendelser`() {
        val person = testPerson
        val hendelse = SøknadInnsendtHendelse(
            UUID.randomUUID(),
            testIdent,
        )
        person.behandle(hendelse)

        println(hendelse.kontekster())
        assertSame(hendelse.aktivitetsteller(), 2)
        assertTrue(hendelse.harAktiviteter())
    }

    @Test
    fun `person kan registrere dager og sende inn rapportering`() {
        val person = testPerson
        val observer = TestObserver().also { person.registrer(it) }
        person.behandle(søknadInnsendtHendelse())
        // Bruker melder inn aktiviteter
        person.behandle(
            nyAktivitetHendelse(
                Aktivitet.Arbeid(LocalDate.now().minusDays(5), 3.2),
            ),
        )
        person.behandle(
            nyAktivitetHendelse(
                Aktivitet.Syk(LocalDate.now().minusDays(4)),
                Aktivitet.Syk(LocalDate.now().minusDays(3)),
            ),
        )
        person.behandle(
            nyAktivitetHendelse(
                Aktivitet.Syk(LocalDate.now().minusDays(2)),
                Aktivitet.Syk(LocalDate.now().minusDays(1)),
            ),
        )
        // Det utløses en ny rapporteringsperiode (enten som konsekvens av søknad eller ny periode)
        person.behandle(
            nyRapporteringsperiodeHendelse(),
        )
        // Bruker sender inn rapportering for en periode
        person.behandle(
            nyRapporteringHendelse(person.__TEST_rapporteringId()),
        )

        assertEquals("Innsendt", observer.tilstand)
        // Bruker sender inn rapportering for samme periode på nytt
        assertThrows<IllegalStateException> {
            person.behandle(
                nyRapporteringHendelse(person.__TEST_rapporteringId()),
            )
        }

        println(person)
    }

    private class TestObserver : PersonObserver {
        lateinit var tilstand: String
        override fun rapporteringsperiodeEndret(event: RapporteringsperiodeObserver.RapporteringsperiodeEndret) {
            tilstand = event.gjeldendeTilstand.toString()
        }
    }
}
