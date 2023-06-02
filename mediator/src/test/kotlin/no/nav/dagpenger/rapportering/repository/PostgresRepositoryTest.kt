package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class PostgresRepositoryTest {
    private val testIdent = "12345678910"

    @Test
    fun `Oppretter en person dersom personen ikke finnes`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person shouldNotBe null
                person!!.ident shouldBe testIdent
            }
        }
    }

    @Test
    fun `lagring en person(er idempotent) og henting av en person`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            repository.lagre(Person(testIdent))

            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person shouldNotBe null
                person!!.ident shouldBe testIdent
            }

            shouldNotThrowAny {
                repository.lagre(Person(testIdent))
            }
        }
    }

    @Test
    fun `lagring og henter en komplett person`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            val person = Person(testIdent).apply {
                behandle(SøknadInnsendtHendelse(UUID.randomUUID(), testIdent))
                behandle(NyAktivitetHendelse(testIdent, aktivRapporteringsperiodeId, Aktivitet.Arbeid(LocalDate.now(), 2)))
            }
            repository.lagre(person)

            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person shouldNotBe null
                person!!.ident shouldBe testIdent

                TestVisitor(person).rapporteringsperioder.size shouldBe 1
                TestVisitor(person).aktiviteter.size shouldBe 1
            }

            shouldNotThrowAny {
                repository.lagre(Person(testIdent))
            }
        }
    }

    private val Person.aktivRapporteringsperiodeId get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId

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
