package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import org.junit.jupiter.api.Test

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

    private class RappoteringsperiodeHenter(person: Person) : RapporteringsperiodVisitor {
//        private val rappoteringsPerioder: Muta
    }
}
