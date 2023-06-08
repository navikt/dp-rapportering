package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.helpers.TestData.godkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.søknadInnsendtHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.TestData.testPerson
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
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
        assertSame(5, hendelse.aktivitetsteller())
        assertTrue(hendelse.harAktiviteter())
    }

    @Test
    fun `person kan registrere dager og sende inn rapportering`() {
        val person = testPerson
        val observer = TestObserver().also { person.registrer(it) }
        person.behandle(søknadInnsendtHendelse())
        val rapporteringsperiodeId = person.aktivRapporteringsperiode
        // Bruker melder inn aktiviteter
        person.behandle(
            nyAktivitetHendelse(
                rapporteringsperiodeId,
                Aktivitet.Arbeid(LocalDate.now().plusDays(5), "PT3H20M"),
            ),
        )
        person.behandle(
            nyAktivitetHendelse(
                rapporteringsperiodeId,
                Aktivitet.Syk(LocalDate.now().plusDays(4)),
            ),
        )
        person.behandle(
            nyAktivitetHendelse(
                rapporteringsperiodeId,
                Aktivitet.Syk(LocalDate.now().plusDays(2)),
            ),
        )
        assertEquals(3, person.antallAktiviteter)
        // Bruker godkjenner rapportering for en periode
        person.behandle(godkjennPeriodeHendelse(rapporteringsperiodeId))
        assertEquals(Godkjent.name, observer.tilstand)
        // Kan ikke rapportere aktivitet etter en periode er godkjent
        val nyAktivitetHendelse = nyAktivitetHendelse(rapporteringsperiodeId, LocalDate.now().plusDays(3))

        assertThrows<IllegalStateException> {
            person.behandle(nyAktivitetHendelse)
        }
        // Bruker sender inn rapportering for samme periode på nytt
        val hendelse = godkjennPeriodeHendelse(rapporteringsperiodeId)
        assertThrows<IllegalStateException> {
            person.behandle(hendelse)
        }

        observer.tilstand shouldBe Godkjent.name
        val fristHendelse = RapporteringsfristHendelse(UUID.randomUUID(), testIdent, LocalDate.now().plusDays(14))
        person.behandle(fristHendelse)

        observer.tilstand shouldBe Innsendt.name

        println(person)
    }

    @Test
    fun `håndterer endringer i rapporteringsplikt`() {
        val person = Person(testIdent)

        // Personer begynner uten rapporertingsplikt
        person.rapporteringsplikt shouldBe RapporteringspliktType.Ingen
        person.nyRapporteringsplikt(RapporteringspliktSøknad(), LocalDate.now().minusDays(2))
        person.rapporteringsplikt shouldBe RapporteringspliktType.Søknad
        person.nyRapporteringsplikt(RapporteringspliktVedtak(), LocalDate.now().minusDays(1))
        person.rapporteringsplikt shouldBe RapporteringspliktType.Vedtak

        person.rapporteringsplikter.size shouldBe 3
    }

    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size
    private val Person.rapporteringsplikt get() = TestVisitor(this).rapporteringspliktType
    private val Person.rapporteringsplikter get() = TestVisitor(this).rapporteringsplikter

    private class TestObserver : PersonObserver {
        lateinit var tilstand: String
        override fun rapporteringsperiodeEndret(event: RapporteringsperiodeObserver.RapporteringsperiodeEndret) {
            tilstand = event.gjeldendeTilstand.toString()
        }

        override fun rapporteringsperiodeInnsendt(event: RapporteringsperiodeObserver.RapporteringsperiodeInnsendt) {}
    }

    private class TestVisitor(person: Person) : PersonVisitor {
        val rapporteringsperioder = mutableListOf<Rapporteringsperiode>()
        val aktiviteter = mutableListOf<Aktivitet>()
        lateinit var rapporteringspliktType: RapporteringspliktType
        val rapporteringsplikter = mutableListOf<Rapporteringsplikt>()

        init {
            person.accept(this)
        }

        override fun visit(
            rapporteringsperiode: Rapporteringsperiode,
            id: UUID,
            periode: ClosedRange<LocalDate>,
            tilstand: Rapporteringsperiode.TilstandType,
            rapporteringsfrist: LocalDate,
        ) {
            rapporteringsperioder += rapporteringsperiode
        }

        override fun visit(
            dag: Dag,
            dato: LocalDate,
            aktiviteter: List<Aktivitet>,
            muligeAktiviter: List<Aktivitet.AktivitetType>,
        ) {
            this.aktiviteter += aktiviteter
        }

        override fun visit(rapporteringsplikt: Rapporteringsplikt, id: UUID, type: RapporteringspliktType) {
            rapporteringspliktType = type
            this.rapporteringsplikter.add(rapporteringsplikt)
        }
    }
}
