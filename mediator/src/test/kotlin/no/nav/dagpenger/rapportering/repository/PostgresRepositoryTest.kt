package no.nav.dagpenger.rapportering.repository

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.Godkjenningsendring.Sluttbruker
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsplikt
import no.nav.dagpenger.rapportering.RapporteringspliktType
import no.nav.dagpenger.rapportering.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.BeregningsdatoPassertHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.hendelser.VedtakInnvilgetHendelse
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
            val person = Person(testIdent).also { repository.lagre(it) }
            person.apply {
                behandle(
                    SøknadInnsendtHendelse(
                        UUID.randomUUID(),
                        testIdent,
                        LocalDateTime.now(),
                        søknadId = UUID.randomUUID(),
                    ),
                )
                person.behandle(
                    RapporteringspliktDatoHendelse(
                        UUID.randomUUID(),
                        testIdent,
                        LocalDateTime.now(),
                        LocalDate.now(),
                    ) { _, tom -> tom },
                )
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

                TestVisitor(lagretPerson).let {
                    it.rapporteringsperioder.size shouldBe 1
                    it.aktiviteter.size shouldBe 1
                    it.rapporteringspliktType shouldBe RapporteringspliktType.Søknad
                }
            }

            shouldNotThrowAny {
                repository.lagre(Person(testIdent))
            }
        }
    }

    @Test
    fun `lagring og henting av en rapporteringsperiode med korrigeringer`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            // Opprett person med innsendt søknad og rapporteringsperiode
            Person(testIdent).let { person ->
                person.behandle(
                    VedtakInnvilgetHendelse(
                        UUID.randomUUID(),
                        testIdent,
                        LocalDate.now(),
                        LocalDateTime.now(),
                        UUID.randomUUID(),
                    ) { _, tom -> tom },
                )
                repository.lagre(person)
            }
            // Godkjenn perioden
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person.behandle(
                    GodkjennPeriodeHendelse(
                        testIdent,
                        person.aktivRapporteringsperiodeId,
                        dato = person.aktivRapporteringsperiode.kanGodkjennesFra,
                    ),
                )
                repository.lagre(person)
            }
            // Verifiser at det føres logg over godkjenning
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                with(person.aktivRapporteringsperiode.godkjenningslogg) {
                    size shouldBe 1
                    first().godkjent() shouldBe true
                    first().kanEndre(Sluttbruker(testIdent)) shouldBe true
                    first().kanEndre(Sluttbruker("fjas")) shouldBe false
                }

                person.behandle(
                    AvgodkjennPeriodeHendelse(
                        testIdent,
                        person.aktivRapporteringsperiode.rapporteringsperiodeId,
                    ),
                )
                repository.lagre((person))
            }

            // Verifiser at det føres logg over avgodkjenning
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                with(person.aktivRapporteringsperiode.godkjenningslogg) {
                    size shouldBe 2
                    last().godkjent() shouldBe false
                    last().kanEndre(Sluttbruker(testIdent)) shouldBe true
                    last().kanEndre(Sluttbruker("fjas")) shouldBe false
                }
            }
            val innsendtRapportering =
                repository.hentEllerOpprettPerson(testIdent).let { person ->
                    person.behandle(
                        GodkjennPeriodeHendelse(
                            testIdent,
                            person.aktivRapporteringsperiodeId,
                            dato = person.aktivRapporteringsperiode.kanGodkjennesFra,
                        ),
                    )
                    person.behandle(BeregningsdatoPassertHendelse(UUID.randomUUID(), testIdent, LocalDate.MAX))
                    repository.lagre(person)

                    person.aktivRapporteringsperiode
                }
            // Opprett en korrigering av innsendt periode
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person.behandle(KorrigerPeriodeHendelse(testIdent, innsendtRapportering.rapporteringsperiodeId))
                repository.lagre(person)
            }
            // Verifiser at innsendt periode har en korrigering
            val sisteKorrigering =
                repository.hentEllerOpprettPerson(testIdent).let { person ->
                    person.aktivRapporteringsperiode.finnSisteKorrigering() shouldNotBe innsendtRapportering
                    person.aktivRapporteringsperiode.finnSisteKorrigering()
                }
            // Opprett en ny korrigering som skal erstatte forrige korrigering
            val nyKorrigering = repository.hentEllerOpprettPerson(testIdent).let { person ->
                person.behandle(KorrigerPeriodeHendelse(testIdent, innsendtRapportering.rapporteringsperiodeId))
                repository.lagre(person)

                person.aktivRapporteringsperiode.finnSisteKorrigering()
            }
            // Verifiser at innsendt periode har en korrigering, men som har erstattet den forrige
            repository.hentEllerOpprettPerson(testIdent).let { person ->
                person.aktivRapporteringsperiode.finnSisteKorrigering().also {
                    it.rapporteringsperiodeId shouldNotBe sisteKorrigering.rapporteringsperiodeId
                    it.rapporteringsperiodeId shouldNotBe innsendtRapportering.rapporteringsperiodeId
                    it.rapporteringsperiodeId shouldBe nyKorrigering.rapporteringsperiodeId
                }
            }

            repository.hentRapporteringsperiode(testIdent, sisteKorrigering.rapporteringsperiodeId).let { periode ->
                periode!!.rapporteringsperiodeId shouldNotBe sisteKorrigering.rapporteringsperiodeId
                periode.finnSisteKorrigering().rapporteringsperiodeId shouldBe nyKorrigering.rapporteringsperiodeId
            }
        }
    }

    @Test
    fun `kan slette en aktivitet`() {
        withMigratedDb {
            val repository = PostgresRepository(dataSource)
            val person = Person(testIdent).apply {
                behandle(
                    SøknadInnsendtHendelse(
                        UUID.randomUUID(),
                        testIdent,
                        LocalDateTime.now(),
                        søknadId = UUID.randomUUID(),
                    ),
                )
                behandle(
                    RapporteringspliktDatoHendelse(
                        UUID.randomUUID(),
                        testIdent,
                        LocalDateTime.now(),
                        LocalDate.now(),
                    ) { _, tom -> tom },
                )
                behandle(
                    NyAktivitetHendelse(
                        testIdent,
                        aktivRapporteringsperiodeId,
                        Aktivitet.Arbeid(LocalDate.now(), 2),
                    ),
                )
            }
            person.antallDager shouldBe 14
            person.antallAktiviteter shouldBe 1

            repository.lagre(person)

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
                lagretPerson.antallDager shouldBe 14
            }
        }
    }

    private val Person.aktivRapporteringsperiodeId get() = TestVisitor(this).rapporteringsperioder.last().rapporteringsperiodeId
    private val Person.aktivRapporteringsperiode get() = TestVisitor(this).rapporteringsperioder.last()
    private val Person.aktivitetId get() = TestVisitor(this).aktivAktivitetId
    private val Person.antallAktiviteter get() = TestVisitor(this).aktiviteter.size
    private val Person.antallDager get() = TestVisitor(this).dager.size
    private val Rapporteringsperiode.godkjenningslogg get() = TestRapporteringsperiodeVisitor(this).godkjenninger

    private class TestRapporteringsperiodeVisitor(rapporteringsperiode: Rapporteringsperiode) :
        RapporteringsperiodVisitor {
        internal val godkjenninger = mutableListOf<Godkjenningsendring>()

        init {
            rapporteringsperiode.accept(this)
        }

        override fun visit(
            godkjenningsendring: Godkjenningsendring,
            id: UUID,
            utførtAv: Godkjenningsendring.Kilde,
            opprettet: LocalDateTime,
            avgodkjent: Godkjenningsendring?,
            begrunnelse: String?,
        ) {
            godkjenninger.add(godkjenningsendring)
        }
    }

    private class TestVisitor(person: Person) : PersonVisitor {
        lateinit var aktivAktivitetId: UUID
        lateinit var rapporteringspliktId: UUID
        lateinit var rapporteringspliktType: RapporteringspliktType
        val rapporteringsperioder = mutableListOf<Rapporteringsperiode>()
        val aktiviteter = mutableListOf<Aktivitet>()
        val dager = mutableListOf<Dag>()

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
            this.dager += dag
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

        override fun visit(
            rapporteringsplikt: Rapporteringsplikt,
            rapporteringspliktId: UUID,
            type: RapporteringspliktType,
        ) {
            this.rapporteringspliktId = rapporteringspliktId
            this.rapporteringspliktType = type
        }
    }
}
