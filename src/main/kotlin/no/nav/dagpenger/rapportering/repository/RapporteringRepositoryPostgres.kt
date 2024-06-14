package no.nav.dagpenger.rapportering.repository

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.util.UUID
import javax.sql.DataSource

class RapporteringRepositoryPostgres(private val dataSource: DataSource) : RapporteringRepository {
    override fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM rapporteringsperiode WHERE id = ? AND ident = ?",
                    id,
                    ident,
                )
                    .map { it.toRapporteringsperiode() }
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

    override fun hentRapporteringsperioder(): List<Rapporteringsperiode> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("SELECT * FROM rapporteringsperiode")
                    .map { it.toRapporteringsperiode() }.asList,
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

    private fun hentDager(rapporteringId: Long): List<Pair<UUID, Dag>> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM dag WHERE rapportering_id = ?",
                    rapporteringId,
                ).map { it.toDagPair() }.asList,
            )
        }

    override fun hentAktiviteter(dagId: UUID): List<Aktivitet> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM aktivitet WHERE dag_id = ?",
                    dagId,
                ).map { it.toAktivitet() }.asList,
            )
        }

    override fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) {
        if (hentRapporteringsperiode(rapporteringsperiode.id, ident) != null) {
            throw RuntimeException("Rapporteringsperioden finnes allerede")
        }
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.lagreRapporteringsperiode(rapporteringsperiode, ident).validateRowsAffected()
                tx.lagreDager(rapporteringsperiode.id, rapporteringsperiode.dager).validateRowsAffected(excepted = 14)
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
                (id, ident, kan_sendes, kan_sendes_fra, kan_korrigeres, brutto_belop, status, registrert_arbeidssoker, fom, tom) 
                VALUES (:id, :ident, :kan_sendes, :kan_sendes_fra, :kan_korrigeres, :brutto_belop, :status, :registrert_arbeidssoker, :fom, :tom)
                """.trimIndent(),
                mapOf(
                    "id" to rapporteringsperiode.id,
                    "ident" to ident,
                    "kan_sendes" to rapporteringsperiode.kanSendes,
                    "kan_sendes_fra" to rapporteringsperiode.kanSendesFra,
                    "kan_korrigeres" to rapporteringsperiode.kanKorrigeres,
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
        this.batchPreparedNamedStatement(
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

    override fun lagreAktiviteter(
        rapporteringId: Long,
        dagId: UUID,
        dag: Dag,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.batchPreparedNamedStatement(
                    """
                    INSERT INTO aktivitet(uuid, dag_id, type, timer) 
                    VALUES (:uuid, :dag_id, :type, :timer) 
                    """.trimIndent(),
                    dag.aktiviteter.map { aktivitet ->
                        mapOf(
                            "uuid" to aktivitet.uuid,
                            "dag_id" to dagId,
                            "type" to aktivitet.type.name,
                            "timer" to aktivitet.timer,
                        )
                    },
                ).sum().validateRowsAffected(excepted = dag.aktiviteter.size)
            }
        }
    }

    override fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
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

    override fun hentDagId(
        rapporteringId: Long,
        dagIdex: Int,
    ): UUID =
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

    override fun oppdaterRapporteringsperiodeFraArena(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                        UPDATE rapporteringsperiode
                        SET kan_sendes = :kan_sendes,
                            kan_korrigeres = :kan_korrigeres,
                            brutto_belop = :brutto_belop,
                            status = :status
                        WHERE id = :id
                        """.trimIndent(),
                        mapOf(
                            "kan_sendes" to rapporteringsperiode.kanSendes,
                            "kan_korrigeres" to rapporteringsperiode.kanKorrigeres,
                            "brutto_belop" to rapporteringsperiode.bruttoBelop,
                            "status" to rapporteringsperiode.status.name,
                            "id" to rapporteringsperiode.id,
                        ),
                    ).asUpdate,
                ).validateRowsAffected()
            }
        }
    }

    override fun oppdaterRapporteringStatus(
        rapporteringId: Long,
        ident: String,
        status: RapporteringsperiodeStatus,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
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

    override fun slettAktiviteter(aktivitetIdListe: List<UUID>) =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.batchPreparedNamedStatement(
                    "DELETE FROM aktivitet WHERE uuid = ?",
                    aktivitetIdListe.map { id ->
                        mapOf("id" to id)
                    },
                ).sum().validateRowsAffected(excepted = aktivitetIdListe.size)
            }
        }
}

private fun Row.toRapporteringsperiode() =
    Rapporteringsperiode(
        id = long("id"),
        kanSendesFra = localDate("kan_sendes_fra"),
        kanSendes = boolean("kan_sendes"),
        kanKorrigeres = boolean("kan_korrigeres"),
        bruttoBelop = doubleOrNull("brutto_belop"),
        status = RapporteringsperiodeStatus.valueOf(string("status")),
        registrertArbeidssoker = stringOrNull("registrert_arbeidssoker").toBooleanOrNull(),
        dager = emptyList(),
        periode =
            Periode(
                fraOgMed = localDate("fom"),
                tilOgMed = localDate("tom"),
            ),
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
        uuid = UUID.fromString(string("uuid")),
        type = AktivitetsType.valueOf(string("type")),
        timer = stringOrNull("timer"),
    )

private fun Int.validateRowsAffected(excepted: Int = 1) {
    if (this != excepted) throw RuntimeException("Expected $this but got $excepted")
}

private fun String?.toBooleanOrNull(): Boolean? = this?.let { this == "t" }
