package no.nav.dagpenger.rapportering.repository

import kotlinx.coroutines.runBlocking
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.metrics.TimedMetrikk.timedAction
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.util.UUID
import javax.sql.DataSource

class RapporteringRepositoryPostgres(
    private val dataSource: DataSource,
) : RapporteringRepository {
    override suspend fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode? =
        timedAction("db-hentRapporteringsperiode") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM rapporteringsperiode WHERE id = ? AND ident = ?",
                        id,
                        ident,
                    ).map { it.toRapporteringsperiode() }
                        .asSingle,
                )
            }?.let {
                it.copy(
                    dager =
                        hentDager(it.id).map { dagPair ->
                            dagPair.second.copy(aktiviteter = hentAktiviteter(dagPair.first))
                        },
                )
            }
        }

    override suspend fun hentLagredeRapporteringsperioder(ident: String): List<Rapporteringsperiode> =
        timedAction("db-hentRapporteringsperioder") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT * FROM rapporteringsperiode where ident = ?", ident)
                        .map { it.toRapporteringsperiode() }
                        .asList,
                )
            }.map { rapporteringsperiode ->
                val dager = hentDager(rapporteringsperiode.id)
                rapporteringsperiode
                    .copy(
                        dager =
                            dager.map { dagPair ->
                                dagPair.second.copy(aktiviteter = hentAktiviteter(dagPair.first))
                            },
                    )
            }
        }

    override suspend fun hentAlleLagredeRapporteringsperioder(): List<Rapporteringsperiode> =
        timedAction("db-hentAlleRapporteringsperioder") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT * FROM rapporteringsperiode")
                        .map { it.toRapporteringsperiode() }
                        .asList,
                )
            }.map { rapporteringsperiode ->
                val dager = hentDager(rapporteringsperiode.id)
                rapporteringsperiode
                    .copy(
                        dager =
                            dager.map { dagPair ->
                                dagPair.second.copy(aktiviteter = hentAktiviteter(dagPair.first))
                            },
                    )
            }
        }

    private fun hentDager(rapporteringId: Long): List<Pair<UUID, Dag>> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM dag WHERE rapportering_id = ?",
                    rapporteringId,
                ).map { it.toDagPair() }.asList,
            )
        }

    override suspend fun hentDagId(
        rapporteringId: Long,
        dagIdex: Int,
    ): UUID =
        timedAction("db-hentDagId") {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            "SELECT id FROM dag WHERE rapportering_id = ? AND dag_index = ?",
                            rapporteringId,
                            dagIdex,
                        ).map { row -> UUID.fromString(row.string("id")) }
                            .asSingle,
                    ) ?: throw RuntimeException("Finner ikke dag med rapporteringID $rapporteringId")
                }
            }
        }

    override suspend fun hentAktiviteter(dagId: UUID): List<Aktivitet> =
        timedAction("db-hentAktiviteter") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM aktivitet WHERE dag_id = ?",
                        dagId,
                    ).map { it.toAktivitet() }.asList,
                )
            }
        }

    override suspend fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) = timedAction("db-lagreRapporteringsperiodeOgDager") {
        if (hentRapporteringsperiode(rapporteringsperiode.id, ident) != null) {
            throw RuntimeException("Rapporteringsperioden finnes allerede")
        }
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.lagreRapporteringsperiode(rapporteringsperiode, ident).validateRowsAffected()
                tx.lagreDager(rapporteringsperiode.id, rapporteringsperiode.dager).validateRowsAffected(excepted = 14)
            }
        }
        rapporteringsperiode.dager.forEach { dag ->
            if (dag.aktiviteter.isNotEmpty()) {
                val dagId = hentDagId(rapporteringsperiode.id, dag.dagIndex)
                lagreAktiviteter(rapporteringsperiode.id, dagId, dag)
            }
        }
    }

    private fun TransactionalSession.lagreRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ): Int =
        this.run(
            queryOf(
                """
                INSERT INTO rapporteringsperiode 
                (id, ident, kan_sendes, kan_sendes_fra, kan_endres, brutto_belop, status, registrert_arbeidssoker, fom, tom) 
                VALUES (:id, :ident, :kan_sendes, :kan_sendes_fra, :kan_endres, :brutto_belop, :status, :registrert_arbeidssoker, :fom, :tom)
                """.trimIndent(),
                mapOf(
                    "id" to rapporteringsperiode.id,
                    "ident" to ident,
                    "kan_sendes" to rapporteringsperiode.kanSendes,
                    "kan_sendes_fra" to rapporteringsperiode.kanSendesFra,
                    "kan_endres" to rapporteringsperiode.kanEndres,
                    "brutto_belop" to rapporteringsperiode.bruttoBelop,
                    "status" to rapporteringsperiode.status.name,
                    "registrert_arbeidssoker" to rapporteringsperiode.registrertArbeidssoker,
                    "fom" to rapporteringsperiode.periode.fraOgMed,
                    "tom" to rapporteringsperiode.periode.tilOgMed,
                ),
            ).asUpdate,
        )

    private fun TransactionalSession.lagreDager(
        rapporteringId: Long,
        dager: List<Dag>,
    ): Int =
        this
            .batchPreparedNamedStatement(
                "INSERT INTO dag (id, rapportering_id, dato, dag_index) VALUES (:id, :rapportering_id, :dato, :dag_index)",
                dager.map { dag ->
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "rapportering_id" to rapporteringId,
                        "dato" to dag.dato,
                        "dag_index" to dag.dagIndex,
                    )
                },
            ).sum()

    override suspend fun lagreAktiviteter(
        rapporteringId: Long,
        dagId: UUID,
        dag: Dag,
    ) = timedAction("db-lagreAktiviteter") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .batchPreparedNamedStatement(
                        """
                        INSERT INTO aktivitet(uuid, dag_id, type, timer) 
                        VALUES (:uuid, :dag_id, :type, :timer) 
                        """.trimIndent(),
                        dag.aktiviteter.map { aktivitet ->
                            mapOf(
                                "uuid" to aktivitet.id,
                                "dag_id" to dagId,
                                "type" to aktivitet.type.name,
                                "timer" to aktivitet.timer,
                            )
                        },
                    ).sum()
                    .validateRowsAffected(excepted = dag.aktiviteter.size)
            }
        }
    }

    override suspend fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    ) = timedAction("db-oppdaterRegistrertArbeidssoker") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET registrert_arbeidssoker = :registrert_arbeidssoker
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "registrert_arbeidssoker" to registrertArbeidssoker,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun oppdaterRapporteringsperiodeFraArena(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) = timedAction("db-oppdaterRapporteringsperiodeFraArena") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET kan_sendes = :kan_sendes,
                                kan_endres = :kan_endres,
                                brutto_belop = :brutto_belop,
                                status = :status
                            WHERE id = :id
                            """.trimIndent(),
                            mapOf(
                                "kan_sendes" to rapporteringsperiode.kanSendes,
                                "kan_endres" to rapporteringsperiode.kanEndres,
                                "brutto_belop" to rapporteringsperiode.bruttoBelop,
                                "status" to rapporteringsperiode.status.name,
                                "id" to rapporteringsperiode.id,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun oppdaterRapporteringStatus(
        rapporteringId: Long,
        ident: String,
        status: RapporteringsperiodeStatus,
    ) = timedAction("db-oppdaterRapporteringStatus") {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET status = :status
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "status" to status.name,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun slettAktiviteter(aktivitetIdListe: List<UUID>) =
        timedAction("db-slettAktiviteter") {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx
                        .batchPreparedNamedStatement(
                            "DELETE FROM aktivitet WHERE uuid = :uuid",
                            aktivitetIdListe.map { id ->
                                mapOf("uuid" to id)
                            },
                        ).sum()
                        .validateRowsAffected(excepted = aktivitetIdListe.size)
                }
            }
        }

    override suspend fun slettRaporteringsperiode(rapporteringId: Long) =
        timedAction("db-slettRaporteringsperiode") {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(
                        // Sjekker at rapporteringsperioden finnes
                        queryOf("SELECT id FROM rapporteringsperiode WHERE id = ?", rapporteringId)
                            .map { row -> row.long("id") }
                            .asSingle,
                    ) ?: throw RuntimeException("Finner ikke rapporteringsperiode med id: $rapporteringId")

                    // Henter ut id for alle dagene i rapporteringsperioden
                    val dagIdListe = (0..13).map { dagIndex -> runBlocking { hentDagId(rapporteringId, dagIndex) } }

                    // Sletter alle aktiviteter assosiert med dagId-ene
                    tx.batchPreparedNamedStatement(
                        "DELETE FROM aktivitet WHERE dag_id = :dag_id",
                        dagIdListe.map { dagId ->
                            mapOf("dag_id" to dagId)
                        },
                    )
                    // Sletter alle dager
                    tx.batchPreparedNamedStatement(
                        "DELETE FROM dag WHERE id = :id",
                        dagIdListe.map { dagId ->
                            mapOf("id" to dagId)
                        },
                    )
                    // Sletter rapporteringsperioden
                    tx
                        .run(
                            queryOf(
                                "DELETE FROM rapporteringsperiode WHERE id = ?",
                                rapporteringId,
                            ).asUpdate,
                        ).validateRowsAffected()
                }
            }
        }

    override suspend fun hentAntallRapporteringsperioder(): Int =
        timedAction("db-hentAntallRapporteringsperioder") {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf("SELECT COUNT(*) FROM rapporteringsperiode")
                        .map { it.int(1) }
                        .asSingle,
                ) ?: 0
            }
        }
}

private fun Int.validateRowsAffected(excepted: Int = 1) {
    if (this != excepted) throw RuntimeException("Expected $excepted but got $this")
}

private fun Row.toRapporteringsperiode() =
    Rapporteringsperiode(
        id = long("id"),
        kanSendesFra = localDate("kan_sendes_fra"),
        kanSendes = boolean("kan_sendes"),
        kanEndres = boolean("kan_endres"),
        bruttoBelop = doubleOrNull("brutto_belop"),
        status = RapporteringsperiodeStatus.valueOf(string("status")),
        registrertArbeidssoker = stringOrNull("registrert_arbeidssoker").toBooleanOrNull(),
        dager = emptyList(),
        periode =
            Periode(
                fraOgMed = localDate("fom"),
                tilOgMed = localDate("tom"),
            ),
        begrunnelseEndring = stringOrNull("begrunnelse_endring"),
    )

private fun Row.toDagPair(): Pair<UUID, Dag> =
    UUID.fromString(string("id")) to
        Dag(
            dato = localDate("dato"),
            aktiviteter = emptyList(),
            dagIndex = int("dag_index"),
        )

private fun Row.toAktivitet() =
    Aktivitet(
        id = UUID.fromString(string("uuid")),
        type = AktivitetsType.valueOf(string("type")),
        timer = stringOrNull("timer"),
    )

private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }
