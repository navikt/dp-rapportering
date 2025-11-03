package no.nav.dagpenger.rapportering.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.config.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.model.MidlertidigLagretData
import java.util.UUID
import javax.sql.DataSource

class JournalfoeringRepositoryPostgres(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : JournalfoeringRepository {
    private val logger = KotlinLogging.logger {}

    override suspend fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: String,
    ) = actionTimer.timedAction("db-lagreJournalpostData") {
        sessionOf(dataSource).use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO opprettede_journalposter (journalpost_id, dokumentinfo_id, rapportering_id) " +
                            "VALUES (?, ?, ?) " +
                            "ON CONFLICT DO NOTHING",
                        journalpostId,
                        dokumentInfoId,
                        rapporteringsperiodeId,
                    ).asUpdate,
                ).let {
                    if (it ==
                        0
                    ) {
                        logger.warn {
                            "Journalpostdata med journalpostId $journalpostId og dokumentInfoId $dokumentInfoId og " +
                                "rapporteringsperiodeId $rapporteringsperiodeId ble ikke l"
                        }
                    }
                } // .validateRowsAffected()
        }
    }

    override suspend fun lagreDataMidlertidig(midlertidigLagretData: MidlertidigLagretData) =
        actionTimer.timedAction("db-lagreDataMidlertidig") {
            sessionOf(dataSource).use { session ->
                session
                    .run(
                        queryOf(
                            "INSERT INTO midlertidig_lagrede_journalposter (id, journalpost, retries) " +
                                "VALUES (?, ?, ?)",
                            UUID.randomUUID().toString(),
                            defaultObjectMapper.writeValueAsString(midlertidigLagretData),
                            0,
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }

    override suspend fun hentMidlertidigLagretData(): List<Triple<String, MidlertidigLagretData, Int>> =
        actionTimer.timedAction("db-hentMidlertidigLagretData") {
            sessionOf(dataSource).use { session ->
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
        }

    override suspend fun sletteMidlertidigLagretData(id: String) =
        actionTimer.timedAction("db-sletteMidlertidigLagretData") {
            sessionOf(dataSource).use { session ->
                session
                    .run(
                        queryOf(
                            "DELETE FROM midlertidig_lagrede_journalposter WHERE id = ?",
                            id,
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }

    override suspend fun oppdaterMidlertidigLagretData(
        id: String,
        retries: Int,
    ) = actionTimer.timedAction("db-oppdaterMidlertidigLagretData") {
        sessionOf(dataSource).use { session ->
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

    override suspend fun hentAntallJournalposter(): Int =
        actionTimer.timedAction("db-hentAntallJournalposter") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT COUNT(*) FROM opprettede_journalposter",
                    ).map {
                        it.int(1)
                    }.asSingle,
                ) ?: 0
            }
        }

    override suspend fun hentAntallMidlertidigLagretData(): Int =
        actionTimer.timedAction("db-hentAntallMidlertidigLagretData") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT COUNT(*) FROM midlertidig_lagrede_journalposter",
                    ).map {
                        it.int(1)
                    }.asSingle,
                ) ?: 0
            }
        }

    override suspend fun hentJournalpostId(rapporteringsperiodeId: String): List<Long> =
        actionTimer.timedAction("db-hentJournalpostId") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT journalpost_id " +
                            "FROM opprettede_journalposter " +
                            "WHERE rapportering_id = ?",
                        rapporteringsperiodeId,
                    ).map {
                        it.long(1)
                    }.asList,
                )
            }
        }

    private fun Int.validateRowsAffected(excepted: Int = 1) {
        if (this != excepted) throw RuntimeException("Expected $excepted but got $this")
    }
}
