package no.nav.dagpenger.rapportering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator
        get() = Mediator(
            rapid,
            PostgresRepository(dataSource),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )
    private val testIdent = "12312312311"
    private val rapporteringspliktDatoHendelse: RapporteringspliktDatoHendelse
        get() = RapporteringspliktDatoHendelse(
            UUID.randomUUID(),
            testIdent,
            LocalDateTime.now(),
            LocalDate.now(),
            LocalDate.now(),
        ) { _, tom -> tom }

    @Test
    fun mediatorflyt() = withMigratedDb {
        val hendelse = SøknadInnsendtHendelse(
            UUID.randomUUID(),
            testIdent,
            LocalDateTime.now(),
            søknadId = UUID.randomUUID(),
        )

        mediator.behandle(hendelse)
        hendelse.behov().map {
            it.type
        } shouldContainExactly listOf(MineBehov.Virkningsdatoer, MineBehov.Søknadstidspunkt)

        mediator.behandle(rapporteringspliktDatoHendelse)

        hendelse.aktivitetsteller() shouldBe 3
        hendelse.harAktiviteter() shouldBe true
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiodeId
        mediator.behandle(NyAktivitetHendelse(testIdent, rapporteringsperiodeId, Aktivitet.Arbeid(LocalDate.now(), 2)))

        mediator.hentEllerOpprettPerson(testIdent).antallAktiviteter shouldBe 1
    }

    @Test
    fun `kan ikke endre på godkjent periode`() = withMigratedDb {
        val hendelse = SøknadInnsendtHendelse(
            UUID.randomUUID(),
            testIdent,
            LocalDateTime.now(),
            søknadId = UUID.randomUUID(),
        )

        mediator.behandle(hendelse)
        mediator.behandle(rapporteringspliktDatoHendelse)
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiodeId
        mediator.behandle(NyAktivitetHendelse(testIdent, rapporteringsperiodeId, Aktivitet.Arbeid(LocalDate.now(), 2)))

        mediator.hentEllerOpprettPerson(testIdent).antallAktiviteter shouldBe 1

        mediator.behandle(GodkjennPeriodeHendelse(testIdent, rapporteringsperiodeId))

        shouldThrow<IllegalStateException> {
            mediator.behandle(
                NyAktivitetHendelse(
                    testIdent,
                    rapporteringsperiodeId,
                    Aktivitet.Arbeid(LocalDate.now(), 2),
                ),
            )
        }
        val sisteAktivitet = mediator.hentEllerOpprettPerson(testIdent).sisteAktivitet
        shouldThrow<IllegalStateException> {
            mediator.behandle(SlettAktivitetHendelse(testIdent, rapporteringsperiodeId, sisteAktivitet.uuid))
        }
    }

    @Test
    fun `godkjente rapporteringsperioder publiseres når fristen har passert`() = withMigratedDb {
        val hendelse =
            SøknadInnsendtHendelse(UUID.randomUUID(), testIdent, LocalDateTime.now(), søknadId = UUID.randomUUID())
        mediator.behandle(hendelse)
        mediator.behandle(rapporteringspliktDatoHendelse)
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiodeId
        mediator.behandle(GodkjennPeriodeHendelse(testIdent, rapporteringsperiodeId))
        val frist = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(14)

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, frist.minusDays(3)))
        rapid.inspektør.size shouldBe 1

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, frist))
        rapid.inspektør.size shouldBe 3
    }

    @Test
    fun `godkjente rapporteringsperioder kan korrigeres`() = withMigratedDb {
        val hendelse =
            SøknadInnsendtHendelse(UUID.randomUUID(), testIdent, LocalDateTime.now(), søknadId = UUID.randomUUID())
        mediator.behandle(hendelse)
        mediator.behandle(rapporteringspliktDatoHendelse)
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiodeId
        mediator.behandle(GodkjennPeriodeHendelse(testIdent, rapporteringsperiodeId))
        val frist = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(14)

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, frist.minusDays(3)))
        rapid.inspektør.size shouldBe 1

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, frist))
        rapid.inspektør.size shouldBe 3

        person.aktivRapporteringsperiode.finnSisteKorrigering() shouldBe person.aktivRapporteringsperiode
        val hendelse1 = KorrigerPeriodeHendelse(UUID.randomUUID(), testIdent, rapporteringsperiodeId)
        mediator.behandle(hendelse1)
        with(mediator.hentEllerOpprettPerson(testIdent)) {
            aktivRapporteringsperiode.finnSisteKorrigering() shouldNotBe person.aktivRapporteringsperiode
        }
    }

    private val Person.aktivRapporteringsperiodeId get() = aktivRapporteringsperiode.rapporteringsperiodeId
    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last()
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size
    private val Person.sisteAktivitet get() = TestVisitor(this).aktiviteter.last()

    private class TestVisitor(person: Person) : PersonVisitor {
        val rapporteringsperioder = mutableListOf<Rapporteringsperiode>()
        val aktiviteter = mutableListOf<Aktivitet>()

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
        ) {
            this.aktiviteter += aktiviteter
        }
    }
}
