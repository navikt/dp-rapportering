package no.nav.dagpenger.rapportering

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.PostgresRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class MediatorTest {
    @Test
    fun `mediatorflyt`() = withMigratedDb {
        val mediator = Mediator(mockk(), PostgresRepository(dataSource))
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

    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size

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
