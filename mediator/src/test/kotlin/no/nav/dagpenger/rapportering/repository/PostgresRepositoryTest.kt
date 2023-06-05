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
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

class PostgresRepositoryTest {
    private val testIdent = "12345678910"

    @Test
    fun `Oppretter en person dersom personen ikke finnes`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person shouldNotBe null
                person.ident shouldBe testIdent
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
                person.ident shouldBe testIdent
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
                behandle(
                    NyAktivitetHendelse(
                        testIdent,
                        aktivRapporteringsperiodeId,
                        Aktivitet.Arbeid(LocalDate.now(), 2),
                    ),
                )
            }
            repository.lagre(person)

            repository.hentEllerOpprettPerson(testIdent).let { lagretPerson ->
                lagretPerson shouldNotBe null
                lagretPerson.ident shouldBe testIdent

                TestVisitor(lagretPerson).rapporteringsperioder.size shouldBe 1
                TestVisitor(lagretPerson).aktiviteter.size shouldBe 1
            }

            shouldNotThrowAny {
                repository.lagre(Person(testIdent))
            }
        }
    }

    @Test
    fun `kan slette en aktivitet`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            val person = Person(testIdent).apply {
                behandle(SøknadInnsendtHendelse(UUID.randomUUID(), testIdent))
                behandle(
                    NyAktivitetHendelse(
                        testIdent,
                        aktivRapporteringsperiodeId,
                        Aktivitet.Arbeid(LocalDate.now(), 2),
                    ),
                )
            }
            repository.lagre(person)

            person.antallAktiviteter shouldBe 1

            repository.hentEllerOpprettPerson(testIdent).let { lagretPerson ->
                lagretPerson.behandle(
                    SlettAktivitetHendelse(
                        testIdent,
                        lagretPerson.aktivRapporteringsperiodeId,
                        lagretPerson.aktivitetId,
                    ),
                )

                repository.lagre(lagretPerson)
            }

            repository.hentEllerOpprettPerson(testIdent).let { lagretPerson ->
                lagretPerson.antallAktiviteter shouldBe 0
            }
        }
    }

    private val Person.aktivRapporteringsperiodeId get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
    private val Person.aktivitetId get() = TestVisitor(this).aktivAktivitetId
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size

    private class TestVisitor(person: Person) : PersonVisitor {
        lateinit var aktivAktivitetId: UUID
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

        override fun visit(
            aktivitet: Aktivitet,
            uuid: UUID,
            dato: LocalDate,
            tid: Duration,
            type: Aktivitet.AktivitetType,
            tilstand: Aktivitet.TilstandType,
        ) {
            aktivAktivitetId = uuid
        }
    }
}
