package no.nav.dagpenger.rapportering.repository

import kotliquery.Query
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.AktivitetType
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.Duration

internal class PostgresRepository(private val ds: DataSource) :
    PersonRepository,
    RapporteringsperiodeRepository {
    override fun hentRapporteringsperiode(ident: String, uuid: UUID): Rapporteringsperiode? {
        TODO("Not yet implemented")
    }

    override fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode> {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT * FROM rapporteringsperiode WHERE person_ident = :ident""",
                    paramMap = mapOf("ident" to ident),
                ).map { row ->
                    val fraOgMed = row.localDate("fom")
                    val tilOgMed = row.localDate("tom")
                    val rapporteringsperiodeId = row.uuid("uuid")
                    val tidslinje = Aktivitetstidslinje(fraOgMed, tilOgMed).apply {
                        hentAktiviteterFor(rapporteringsperiodeId).forEach { leggTilAktivitet(it) }
                    }
                    Rapporteringsperiode.rehydrer(
                        rapporteringsperiodeId,
                        row.localDate("rapporteringsfrist"),
                        fraOgMed,
                        tilOgMed,
                        Rapporteringsperiode.TilstandType.valueOf(row.string("tilstand")),
                        row.localDateTime("opprettet"),
                        tidslinje,
                    )
                }.asList,
            )
        }
    }

    private fun hentAktiviteterFor(rapporteringsperiodeId: UUID): List<Aktivitet> {
        return using(sessionOf(ds)) { session ->
            session.run(queryOf("SET intervalstyle=iso_8601").asExecute)
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """
                        |SELECT * FROM aktivitet 
                        |LEFT JOIN dag d ON aktivitet.uuid = d.aktivitet_id
                        |WHERE d.rapporteringsperiode_id = :rapporteringsperiodeId
                    """.trimMargin(),
                    paramMap = mapOf("rapporteringsperiodeId" to rapporteringsperiodeId),
                ).map { row ->
                    val type = AktivitetType.valueOf(row.string("type"))
                    val dato = row.localDate("dato")
                    when (type) {
                        AktivitetType.Arbeid -> Aktivitet.Arbeid(dato, Duration.parse(row.string("tid")).inWholeHours)
                        AktivitetType.Syk -> Aktivitet.Syk(dato)
                        AktivitetType.Ferie -> Aktivitet.Ferie(dato)
                    }
                }.asList,
            )
        }
    }

    override fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate): Rapporteringsperiode? {
        TODO("Not yet implemented")
    }

    private fun hentPerson(ident: String): Person? {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT ident FROM person WHERE ident = :ident""",
                    paramMap = mapOf("ident" to ident),
                ).map { row ->
                    Person(
                        row.string("ident"),
                        hentRapporteringsperioder(ident),
                    )
                }.asSingle,
            )
        }
    }

    override fun hentEllerOpprettPerson(ident: String): Person {
        return if (personFinnes(ident)) {
            hentPerson(ident)!!
        } else {
            insertPerson(ident)
            Person(ident)
        }
    }

    override fun lagre(person: Person) {
        using(sessionOf(ds)) { session ->
            session.transaction { tx ->
                val queries = LagrePersonStatementBuilder(person).queries
                queries.forEach { query ->
                    tx.run(query.asUpdate)
                    println(query)
                }
            }
        }
    }

    private fun insertPerson(ident: String) {
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """INSERT INTO person(ident) VALUES (:ident)""",
                    paramMap = mapOf("ident" to ident),
                ).asUpdate,
            )
        }
    }

    private fun personFinnes(ident: String) = using(sessionOf(ds)) { session ->
        session.run(
            queryOf(
                //language=PostgreSQL
                statement = """SELECT EXISTS(SELECT 1 FROM person WHERE ident = :ident) AS finnes""",
                paramMap = mapOf("ident" to ident),
            ).map { row ->
                row.boolean("finnes")
            }.asSingle,
        )
    } ?: false
}

private class LagrePersonStatementBuilder(person: Person) : PersonVisitor, RapporteringsperiodVisitor, DagVisitor {
    private lateinit var rapporteringsperiodeId: UUID
    private lateinit var ident: String
    var queries = mutableListOf<Query>()

    init {
        person.accept(this)
    }

    override fun visit(person: Person, ident: String) {
        this.ident = ident
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """INSERT INTO person(ident) VALUES (:ident) ON CONFLICT DO NOTHING""",
                paramMap = mapOf("ident" to ident),
            ),
        )
    }

    override fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
        rapporteringsfrist: LocalDate,
    ) {
        this.rapporteringsperiodeId = id
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO rapporteringsperiode (uuid, person_ident, tilstand, rapporteringsfrist, fom, tom)
                    VALUES (:uuid,
                            :ident,
                            :tilstand,
                            :rapporteringsfrist,
                            :fraOgMed,
                            :tilOgMed)
                    ON CONFLICT (uuid) DO UPDATE SET tilstand = :tilstand
                """.trimIndent(),
                paramMap = mapOf(
                    "uuid" to id,
                    "ident" to ident,
                    "rapporteringsfrist" to rapporteringsfrist,
                    "tilstand" to tilstand.name,
                    "fraOgMed" to periode.start,
                    "tilOgMed" to periode.endInclusive,
                ),
            ),
        )
    }

    override fun visit(
        aktivitet: Aktivitet,
        uuid: UUID,
        dato: LocalDate,
        tid: Duration,
        type: AktivitetType,
        tilstand: Aktivitet.TilstandType,
    ) {
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                INSERT INTO aktivitet(uuid,
                                      person_ident,
                                      tilstand,
                                      dato,
                                      "type",
                                      tid)
                VALUES (:uuid,
                        :ident,
                        :tilstand,
                        :dato,
                        :type,
                        :tid::interval)
                ON CONFLICT (uuid) DO UPDATE SET tilstand = :tilstand
                """.trimIndent(),
                paramMap = mapOf(
                    "uuid" to aktivitet.uuid,
                    "ident" to ident,
                    "tilstand" to tilstand.name,
                    "dato" to dato,
                    "type" to type.name,
                    "tid" to tid.toIsoString(),
                ),
            ),
        )
        queries.add(
            queryOf(
                //language=PostgreSQL
                statement = """
                    INSERT INTO dag(rapporteringsperiode_id, aktivitet_id)
                    VALUES (:rapporteringsperiodeId, :aktivitetId)
                    ON CONFLICT DO NOTHING
                """.trimIndent(),
                paramMap = mapOf(
                    "rapporteringsperiodeId" to rapporteringsperiodeId,
                    "aktivitetId" to aktivitet.uuid,
                ),
            ),
        )
    }
}
