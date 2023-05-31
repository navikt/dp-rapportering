package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import org.postgresql.util.PGInterval
import java.util.UUID
import javax.sql.DataSource
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
        TODO("Not yet implemented")
    }

    override fun hentEllerOpprettPerson(ident: String): Person {
        TODO("Not yet implemented")
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
}

fun main() {
    7.5.toDuration(DurationUnit.HOURS).toIsoString().also {
        println(it)
    }.let {
        PGInterval(it).also {
            println(it)
        }
    }.let {
        it.minutes.toDuration(DurationUnit.MINUTES) + it.hours.toDuration(DurationUnit.HOURS)
    }.let {
        println(it.toIsoString())
    }
}
