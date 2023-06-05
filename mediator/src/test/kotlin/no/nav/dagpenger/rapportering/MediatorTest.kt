package no.nav.dagpenger.rapportering

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringsfristHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MediatorTest {
    private val rapid = TestRapid()
    private val mediator get() = Mediator(rapid, PostgresRepository(dataSource))

    @Test
    fun `mediatorflyt`() = withMigratedDb {
        val testIdent = "12312312311"
        val hendelse = SøknadInnsendtHendelse(
            UUID.randomUUID(),
            testIdent,
        )

        mediator.behandle(hendelse)

        hendelse.aktivitetsteller() shouldBe 2
        hendelse.harAktiviteter() shouldBe true
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiode
        mediator.behandle(NyAktivitetHendelse(testIdent, rapporteringsperiodeId, Aktivitet.Arbeid(LocalDate.now(), 2)))

        mediator.hentEllerOpprettPerson(testIdent).antallAktiviteter shouldBe 1
    }

    @Test
    fun `kan ikke endre på godkjent periode`() = withMigratedDb {
        val testIdent = "12312312311"
        val hendelse = SøknadInnsendtHendelse(
            UUID.randomUUID(),
            testIdent,
        )

        mediator.behandle(hendelse)
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiode
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
    fun `godkjente rapporteringsperioder publiseres`() = withMigratedDb {
        val testIdent = "12312312311"
        val hendelse = SøknadInnsendtHendelse(UUID.randomUUID(), testIdent)
        mediator.behandle(hendelse)
        val person = mediator.hentEllerOpprettPerson(testIdent)
        val rapporteringsperiodeId = person.aktivRapporteringsperiode
        mediator.behandle(GodkjennPeriodeHendelse(testIdent, rapporteringsperiodeId))

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, LocalDate.now()))
        rapid.inspektør.size shouldBe 1

        mediator.behandle(RapporteringsfristHendelse(UUID.randomUUID(), testIdent, LocalDate.now().plusDays(14)))
        rapid.inspektør.size shouldBe 2
    }

    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
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
    }
}
