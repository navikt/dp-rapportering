package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.model.MidlertidigLagretData
import javax.sql.DataSource

class JournalfoeringRepositoryPostgres(
    private val dataSource: DataSource,
) : JournalfoeringRepository {
    override fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: Long,
    ) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO opprettede_journalposter (journalpost_id, dokumentinfo_id, rapportering_id) " +
                            "VALUES (?, ?, ?)",
                        journalpostId,
                        dokumentInfoId,
                        rapporteringsperiodeId,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override fun lagreDataMidlertidig(midlertidigLagretData: MidlertidigLagretData) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO midlertidig_lagrede_journalposter (id, journalpost, retries) " +
                            "VALUES (?, ?, ?)",
                        midlertidigLagretData.rapporteringsperiode.id,
                        defaultObjectMapper.writeValueAsString(midlertidigLagretData),
                        0,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override fun hentMidlertidigLagretData(): List<Triple<String, MidlertidigLagretData, Int>> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT id, journalpost, retries FROM midlertidig_lagrede_journalposter FOR UPDATE SKIP LOCKED",
                ).map {
                    Triple(
                        it.string("id"),
                        defaultObjectMapper.readValue(it.string("journalpost"), MidlertidigLagretData::class.java),
                        it.int("retries"),
                    )
                }.asList,
            )
        }

    override fun sletteMidlertidigLagretData(id: String) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "DELETE FROM midlertidig_lagrede_journalposter WHERE id = ?",
                        id,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override fun oppdaterMidlertidigLagretData(
        id: String,
        retries: Int,
    ) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "UPDATE midlertidig_lagrede_journalposter SET retries = ? WHERE id = ?",
                        retries,
                        id,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override fun hentAntallJournalposter(): Int =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT COUNT(*) FROM opprettede_journalposter",
                ).map {
                    it.int(1)
                }.asSingle,
            ) ?: 0
        }

    override fun hentAntallMidlertidigLagretData(): Int =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT COUNT(*) FROM midlertidig_lagrede_journalposter",
                ).map {
                    it.int(1)
                }.asSingle,
            ) ?: 0
        }

    private fun Int.validateRowsAffected(excepted: Int = 1) {
        if (this != excepted) throw RuntimeException("Expected $excepted but got $this")
    }
}
