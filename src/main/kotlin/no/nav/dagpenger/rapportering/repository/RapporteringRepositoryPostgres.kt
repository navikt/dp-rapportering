package no.nav.dagpenger.rapportering.repository

import kotliquery.Row
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

    private fun hentAktiviteter(dagId: UUID): List<Aktivitet> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT * FROM aktivitet WHERE dag_id = ?",
                    dagId,
                ).map { it.toAktivitet() }.asList,
            )
        }

    override fun lagreRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        """
                        INSERT INTO rapporteringsperiode 
                        (id, ident, kan_sendes, kan_sendes_fra, kan_korrigeres, brutto_belop, status, registrert_arbeidssoker, fom, tom) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        rapporteringsperiode.id,
                        ident,
                        rapporteringsperiode.kanSendes,
                        rapporteringsperiode.kanSendesFra,
                        rapporteringsperiode.kanKorrigeres,
                        rapporteringsperiode.bruttoBelop,
                        rapporteringsperiode.status.name,
                        rapporteringsperiode.registrertArbeidssoker,
                        rapporteringsperiode.periode.fraOgMed,
                        rapporteringsperiode.periode.tilOgMed,
                    ).asUpdate,
                )
                rapporteringsperiode.dager.map { dag ->
                    tx.run(
                        queryOf(
                            "INSERT INTO dag (id, rapportering_id, dato, dag_index) VALUES (?, ?, ?, ?)",
                            UUID.randomUUID(),
                            rapporteringsperiode.id,
                            dag.dato,
                            dag.dagIndex,
                        ).asUpdate,
                    )
                }
            }
        }
    }

    override fun lagreAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    ) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                val dagId =
                    tx.run(
                        queryOf(
                            "SELECT id FROM dag WHERE rapportering_id = ? AND dag_index = ?",
                            rapporteringId,
                            dag.dagIndex,
                        ).map { row -> UUID.fromString(row.string("id")) }
                            .asSingle,
                    )
                dag.aktiviteter.forEach { aktivitet ->
                    tx.run(
                        queryOf(
                            "INSERT INTO aktivitet(uuid, dag_id, type, timer) VALUES (?, ?, ?, ?)",
                            aktivitet.uuid,
                            dagId,
                            aktivitet.type.name,
                            aktivitet.timer,
                        ).asUpdate,
                    )
                }
            }
        }
    }

    override fun slettAktivitet(aktivitetId: UUID): Int =
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "DELETE FROM aktivitet WHERE uuid = ?",
                        aktivitetId,
                    ).asUpdate,
                )
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
        registrertArbeidssoker = stringOrNull("registrert_arbeidssoker")?.toBoolean(),
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
