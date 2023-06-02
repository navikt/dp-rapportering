package no.nav.dagpenger.rapportering.repository

import kotliquery.Query
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.PersonVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.util.UUID
import javax.sql.DataSource

internal class PostgresRepository(private val ds: DataSource) :
    PersonRepository,
    AktivitetRepository,
    RapporteringsperiodeRepository {
    override fun hentAktivitet(ident: String, uuid: UUID): Aktivitet {
        TODO()
//        using(sessionOf(ds)) { session ->
//            session.run(
//                //language=PostgreSQL
//                queryOf(
//                    statement = """
//                    SELECT tilstand, dato, type, tid  FROM aktivitet WHERE person_ident = :ident AND uuid = :uuid
//                    """.trimIndent(),
//                    paramMap = mapOf(
//                        "ident" to ident,
//                        "uuid" to uuid,
//
//                    ),
//                ).map { row ->
//                    row.l
//                    TODO()
// //                    Aktivitet.rehydrer(
// //                        dato = row.localDate("dato"),
// //                        tid = r,
// //                        type = "",
// //                        uuid =,
// //                        tilstand = ""
// //
// //                    )
//                }.asSingle,
//            )
//        }
    }

    override fun hentAktiviteter(ident: String): List<Aktivitet> {
        TODO("Not yet implemented")
    }

    override fun leggTilAktiviteter(ident: String, aktiviteter: List<Aktivitet>): Boolean {
        TODO("Not yet implemented")
    }

    override fun slettAktivitet(ident: String, uuid: UUID): Boolean? {
        TODO("Not yet implemented")
    }

    override fun hentPerson(ident: String): Person? {
        return using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    //language=PostgreSQL
                    statement = """SELECT ident FROM person where ident = :ident""",
                    paramMap = mapOf("ident" to ident),

                ).map { row ->
                    Person(row.string("ident"))
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
                tx.run(LagrePersonStatementBuilder(person).query.asUpdate)
            }
        }
    }

    override fun hentRapporteringsperiode(ident: String, uuid: UUID): Rapporteringsperiode? {
        TODO("Not yet implemented")
    }

    override fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode> {
        TODO("Not yet implemented")
    }

    override fun lagreRapporteringsperiode(ident: String, rapporteringsperiode: Rapporteringsperiode): Boolean {
        TODO("Not yet implemented")
    }

    private fun insertPerson(ident: String) {
        using(sessionOf(ds)) { session ->
            //language=PostgreSQL
            session.run(
                queryOf(
                    statement = """INSERT INTO person(ident) values (:ident)""",
                    paramMap = mapOf("ident" to ident),
                ).asUpdate,
            )
        }
    }

    private fun personFinnes(ident: String): Boolean {
        return using(sessionOf(ds)) { session ->
            //language=PostgreSQL
            session.run(
                queryOf(
                    statement = """SELECT exists(SELECT 1 FROM person WHERE ident = :ident) AS finnes""",
                    paramMap = mapOf("ident" to ident),
                ).map { row ->
                    row.boolean("finnes")
                }.asSingle,
            )
        } ?: false
    }
}

private class LagrePersonStatementBuilder(person: Person) : PersonVisitor {
    lateinit var query: Query

    init {
        person.accept(this)
    }

    override fun visit(person: Person, ident: String) {
        query = queryOf(
            //language=PostgreSQL
            statement = """INSERT INTO person(ident) VALUES (:ident) ON CONFLICT DO NOTHING""",
            paramMap = mapOf("ident" to ident),
        )
    }
}
