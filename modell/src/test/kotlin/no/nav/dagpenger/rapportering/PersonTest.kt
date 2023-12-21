package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.RapporteringspliktType.Ingen
import no.nav.dagpenger.rapportering.RapporteringspliktType.Søknad
import no.nav.dagpenger.rapportering.RapporteringspliktType.Vedtak
import no.nav.dagpenger.rapportering.helpers.TestData.godkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyAktivitetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.nyVedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.søknadInnsendtHendelse
import no.nav.dagpenger.rapportering.helpers.TestData.testIdent
import no.nav.dagpenger.rapportering.helpers.TestData.testPerson
import no.nav.dagpenger.rapportering.helpers.januar
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakAvslåttHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class PersonTest {
    @Test
    fun `person håndterer hendelser`() {
        val person = testPerson
        val hendelse = søknadInnsendtHendelse()
        person.behandle(hendelse)

        assertSame(2, hendelse.aktivitetsteller())
        assertTrue(hendelse.harAktiviteter())
    }

    @Test
    fun `person kan registrere dager og sende inn rapportering`() {
        val person = testPerson
        val observer = TestObserver().also { person.registrer(it) }
        person.behandle(søknadInnsendtHendelse())
        person.behandle(
            RapporteringspliktDatoHendelse(
                UUID.randomUUID(),
                testIdent,
                LocalDateTime.now(),
                LocalDate.now(),
            ) { fom, _ -> fom },
        )
        val rapporteringsperiodeId = person.aktivRapporteringsperiodeId

        // Bruker melder inn aktiviteter
        person.behandle(
            nyAktivitetHendelse(
                rapporteringsperiodeId,
                Aktivitet.Arbeid(LocalDate.now().plusDays(8), "PT3H20M"),
            ),
        )
        person.behandle(
            nyAktivitetHendelse(
                rapporteringsperiodeId,
                Aktivitet.Syk(LocalDate.now().plusDays(3)),
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
        person.behandle(
            godkjennPeriodeHendelse(
                rapporteringsperiodeId,
                person.aktivRapporteringsperiode.kanGodkjennesFra,
            ),
        )
        assertEquals(Godkjent.name, observer.tilstand)
        // Kan ikke rapportere aktivitet etter en periode er godkjent
        val nyAktivitetHendelse = nyAktivitetHendelse(rapporteringsperiodeId, LocalDate.now().plusDays(3))

        assertThrows<IllegalStateException> {
            person.behandle(nyAktivitetHendelse)
        }
        // Bruker sender inn rapportering for samme periode på nytt
        val hendelse =
            godkjennPeriodeHendelse(rapporteringsperiodeId, person.aktivRapporteringsperiode.kanGodkjennesFra)
        assertThrows<IllegalStateException> {
            person.behandle(hendelse)
        }

        // Rapporteringsperioder skal ikke sendes inn før person har rapporteringsplikt pga vedtak i perioden
        observer.tilstand shouldBe Godkjent.name
        val fristHendelse = BeregningsdatoPassertHendelse(UUID.randomUUID(), testIdent, LocalDate.now().plusDays(14))
        person.behandle(fristHendelse)

        observer.tilstand shouldBe Godkjent.name
        person.behandle(nyVedtakInnvilgetHendelse())
        person.behandle(
            BeregningsdatoPassertHendelse(UUID.randomUUID(), testIdent, LocalDate.now().plusDays(14)),
        )
        observer.tilstand shouldBe Innsendt.name
    }

    @Test
    fun `håndterer endringer i rapporteringsplikt`() {
        val person = Person(testIdent)
        // Personer begynner uten rapporertingsplikt
        person.rapporteringspliktType shouldBe Ingen
        person.nyRapporteringsplikt(
            RapporteringspliktSøknad(
                rapporteringspliktFra = LocalDateTime.now().minusSeconds(3),
            ),
        )
        person.rapporteringspliktType shouldBe Søknad
        person.nyRapporteringsplikt(
            RapporteringspliktVedtak(
                rapporteringspliktFra = LocalDateTime.now(),
                sakId = UUID.randomUUID(),
            ),
        )
        person.rapporteringspliktType shouldBe Vedtak

        person.rapporteringsplikter.size shouldBe 3
    }

    @Test
    fun `vedtak med utfall avslag gir IngenRapporteringsplikt fom virkningsdato uavhengig av nåværende rapporteringsplikt`() {
        val person = Person(testIdent)

        person.rapporteringspliktType shouldBe Ingen
        person.behandle(
            VedtakAvslåttHendelse(
                UUID.randomUUID(),
                testIdent,
                virkningsdato = 1.januar,
                opprettet = 4.januar.atStartOfDay(),
                UUID.randomUUID(),
            ),
        )
        person.rapporteringspliktType shouldBe Ingen

        person.nyRapporteringsplikt(RapporteringspliktSøknad(rapporteringspliktFra = 1.januar.atStartOfDay()))
        person.rapporteringspliktType shouldBe Søknad
        person.behandle(
            VedtakAvslåttHendelse(
                UUID.randomUUID(),
                testIdent,
                virkningsdato = 1.januar,
                opprettet = 4.januar.atStartOfDay(),
                UUID.randomUUID(),
            ),
        )
        person.rapporteringspliktType shouldBe Ingen

        person.nyRapporteringsplikt(
            RapporteringspliktVedtak(
                rapporteringspliktFra = 1.januar.atStartOfDay(),
                sakId = UUID.randomUUID(),
            ),
        )
        person.rapporteringspliktType shouldBe Vedtak
        person.behandle(
            VedtakAvslåttHendelse(
                UUID.randomUUID(),
                testIdent,
                virkningsdato = 1.januar,
                opprettet = 4.januar.atStartOfDay(),
                UUID.randomUUID(),
            ),
        )
        person.rapporteringspliktType shouldBe Ingen
        person.rapporteringsplikt.rapporteringspliktFra shouldBe 1.januar.atStartOfDay()
    }

    @Test
    fun `En innvilgelse for person med RapporteringspliktSøknad gir RapporteringspliktVedtak fom virkningsdato`() {
        val person = Person(testIdent)

        person.nyRapporteringsplikt(RapporteringspliktSøknad(rapporteringspliktFra = 1.januar.atStartOfDay()))
        person.rapporteringspliktType shouldBe Søknad
        person.rapporteringsplikt.rapporteringspliktFra shouldBe 1.januar.atStartOfDay()

        person.behandle(nyVedtakInnvilgetHendelse(2.januar))

        person.rapporteringspliktType shouldBe Vedtak
        person.rapporteringsplikt.rapporteringspliktFra shouldBe 2.januar.atStartOfDay()
    }

    private val Person.aktivRapporteringsperiodeId get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last()
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size
    private val Person.rapporteringspliktType get() = TestVisitor(this).rapporteringspliktType
    private val Person.rapporteringsplikt get() = TestVisitor(this).rapporteringsplikter.last()
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
            beregnesEtter: LocalDate,
            korrigerer: Rapporteringsperiode?,
            korrigertAv: Rapporteringsperiode?,
        ) {
            rapporteringsperioder += rapporteringsperiode
        }

        override fun visit(
            dag: Dag,
            dato: LocalDate,
            aktiviteter: List<Aktivitet>,
            muligeAktiviter: List<Aktivitet.AktivitetType>,
            strategi: Dag.StrategiType,
        ) {
            this.aktiviteter += aktiviteter
        }

        override fun visit(
            rapporteringsplikt: Rapporteringsplikt,
            id: UUID,
            type: RapporteringspliktType,
        ) {
            rapporteringspliktType = type
            this.rapporteringsplikter.add(rapporteringsplikt)
        }
    }
}
