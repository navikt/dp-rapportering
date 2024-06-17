package no.nav.dagpenger.rapportering.repository

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.model.Journalpost
import javax.sql.DataSource

class JournalfoeringRepositoryPostgres(
    private val dataSource: DataSource,
) : JournalfoeringRepository {
    private val objectMapper =
        ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

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

    override fun lagreJournalpostMidlertidig(
        rapporteringsperiodeId: Long,
        journalpost: Journalpost,
    ) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO midlertidig_lagrede_journalposter (id, journalpost, retries) " +
                            "VALUES (?, ?, ?)",
                        rapporteringsperiodeId,
                        objectMapper.writeValueAsString(journalpost),
                        0,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override fun hentMidlertidigLagredeJournalposter(): List<Triple<String, Journalpost, Int>> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT id, journalpost, retries FROM midlertidig_lagrede_journalposter FOR UPDATE SKIP LOCKED",
                ).map {
                    Triple(
                        it.string("id"),
                        objectMapper.readValue(it.string("journalpost"), Journalpost::class.java),
                        it.int("retries"),
                    )
                }.asList,
            )
        }

    override fun hentJournalpostData(journalpostId: Long): List<Triple<Long, Long, Long>> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT journalpost_id, dokumentinfo_id, rapportering_id " +
                        "FROM opprettede_journalposter " +
                        "WHERE journalpost_id = ?",
                    journalpostId,
                ).map {
                    Triple(
                        it.long("journalpost_id"),
                        it.long("dokumentinfo_id"),
                        it.long("rapportering_id"),
                    )
                }.asList,
            )
        }

    override fun sletteMidlertidigLagretJournalpost(id: String) {
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

    override fun oppdaterMidlertidigLagretJournalpost(
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

    private fun Int.validateRowsAffected(excepted: Int = 1) {
        if (this != excepted) throw RuntimeException("Expected $this but got $excepted")
    }
}
