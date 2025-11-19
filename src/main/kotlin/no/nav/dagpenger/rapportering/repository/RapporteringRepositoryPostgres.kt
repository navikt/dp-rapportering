package no.nav.dagpenger.rapportering.repository

import kotlinx.coroutines.runBlocking
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.connector.toAdapterKortType
import no.nav.dagpenger.rapportering.connector.toKortType
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Aktivitet.AktivitetsType
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.KortType
import no.nav.dagpenger.rapportering.model.Periode
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class RapporteringRepositoryPostgres(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : RapporteringRepository {
    override suspend fun hentRapporteringsperiode(
        id: String,
        ident: String,
    ): Rapporteringsperiode? =
        actionTimer.timedAction("db-hentRapporteringsperiode") {
            sessionOf(dataSource)
                .use { session ->
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
                            hentDagerUtenAktivitet(it.id).map { dagPair ->
                                dagPair.second.copy(aktiviteter = hentAktiviteter(dagPair.first))
                            },
                    )
                }
        }

    override suspend fun finnesRapporteringsperiode(
        id: String,
        ident: String,
    ): Boolean =
        actionTimer.timedAction("db-finnesRapporteringsperiode") {
            sessionOf(dataSource)
                .use { session ->
                    session.run(
                        queryOf("SELECT * FROM rapporteringsperiode WHERE id = ? AND ident = ?", id, ident)
                            .map { it.toRapporteringsperiode() }
                            .asSingle,
                    )
                }.let { it != null }
        }

    override suspend fun hentRapporteringsperiodeIdForInnsendtePerioder(): List<String> =
        actionTimer.timedAction("db-hentRapporteringsperiodeIdForInnsendtePerioder") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT id FROM rapporteringsperiode WHERE status = ? AND mottatt_dato <= CURRENT_DATE - INTERVAL '5 days'",
                        RapporteringsperiodeStatus.Innsendt.name,
                    ).map { it.string("id") }
                        .asList,
                )
            }
        }

    override suspend fun hentRapporteringsperiodeIdForMidlertidigePerioder(): List<String> =
        actionTimer.timedAction("db-hentRapporteringsperiodeIdForMidlertidigePerioder") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT id FROM rapporteringsperiode WHERE status = ?",
                        RapporteringsperiodeStatus.Midlertidig.name,
                    ).map { it.string("id") }
                        .asList,
                )
            }
        }

    // Vi må slette gamle meldekort av hensyn til personvernet
    // Men vi må samtidig gi nok tid slik at man kan sende meldekort selv om frist for trekk er passert
    // Bruker tas ut av arbeidssøkerregisteret når det har gått mer enn 20 dager siden siste innsendt meldekort
    // Da er det OK å slette meldekort etter 30 dager fra TOM-datoen
    override suspend fun hentRapporteringsperiodeIdForPerioderEtterSisteFrist(): List<String> =
        actionTimer.timedAction("db-hentRapporteringsperiodeIdForPerioderEtterSisteFrist") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf("SELECT id FROM rapporteringsperiode WHERE tom <= CURRENT_DATE - INTERVAL '30 days'")
                        .map { it.string("id") }
                        .asList,
                )
            }
        }

    override suspend fun hentLagredeRapporteringsperioder(ident: String): List<Rapporteringsperiode> =
        actionTimer.timedAction("db-hentRapporteringsperioder") {
            sessionOf(dataSource)
                .use { session ->
                    session.run(
                        queryOf("SELECT * FROM rapporteringsperiode where ident = ?", ident)
                            .map { it.toRapporteringsperiode() }
                            .asList,
                    )
                }.map { rapporteringsperiode ->
                    val dager = hentDagerUtenAktivitet(rapporteringsperiode.id)
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
        actionTimer.timedAction("db-hentAlleRapporteringsperioder") {
            sessionOf(dataSource)
                .use { session ->
                    session.run(
                        queryOf("SELECT * FROM rapporteringsperiode")
                            .map { it.toRapporteringsperiode() }
                            .asList,
                    )
                }.map { rapporteringsperiode ->
                    val dager = hentDagerUtenAktivitet(rapporteringsperiode.id)
                    rapporteringsperiode
                        .copy(
                            dager =
                                dager.map { dagPair ->
                                    dagPair.second.copy(aktiviteter = hentAktiviteter(dagPair.first))
                                },
                        )
                }
        }

    // OBS! Denne funksjonen henter dager uten aktiviteter. Aktiviteter må hentes separat.
    override suspend fun hentDagerUtenAktivitet(rapporteringId: String): List<Pair<UUID, Dag>> =
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    "SELECT * FROM dag WHERE rapportering_id = ?",
                    rapporteringId,
                ).map { it.toDagPair() }.asList,
            )
        }

    override suspend fun hentDagId(
        rapporteringId: String,
        dagIdex: Int,
    ): UUID =
        actionTimer.timedAction("db-hentDagId") {
            sessionOf(dataSource).use { session ->
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
        actionTimer.timedAction("db-hentAktiviteter") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT * FROM aktivitet WHERE dag_id = ?",
                        dagId,
                    ).map { it.toAktivitet() }.asList,
                )
            }
        }

    override suspend fun hentKanSendes(rapporteringId: String): Boolean? =
        actionTimer.timedAction("db-hentKanSendes") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT kan_sendes FROM rapporteringsperiode WHERE id = ?",
                        rapporteringId,
                    ).map { it.boolean("kan_sendes") }
                        .asSingle,
                )
            }
        }

    override suspend fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    ) = actionTimer.timedAction("db-lagreRapporteringsperiodeOgDager") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                // Vi skal få 1 når INSERT i lagreRapporteringsperiode går som normalt
                // Vi skal få 0 når INSERT i lagreRapporteringsperiode får CONFLICT
                // Men siden vi har ON CONFLICT DO NOTHING, skal vi ikke få feil og skal bare svare OK
                // lagreRapporteringsperiode skal kaste exception når noe annet er galt
                if (tx.lagreRapporteringsperiode(rapporteringsperiode, ident) == 1) {
                    tx
                        .lagreDager(rapporteringsperiode.id, rapporteringsperiode.dager)
                        .validateRowsAffected(excepted = 14)
                }
            }
        }
        rapporteringsperiode.dager.forEach { dag ->
            if (dag.aktiviteter.isNotEmpty()) {
                val dagId = hentDagId(rapporteringsperiode.id, dag.dagIndex)
                slettOgLagreAktiviteter(rapporteringsperiode.id, dagId, dag)
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
                (id, type, ident, kan_sendes, kan_sendes_fra, kan_endres, brutto_belop, status, registrert_arbeidssoker, fom, tom, original_id, rapporteringstype) 
                VALUES (:id, :type, :ident, :kan_sendes, :kan_sendes_fra, :kan_endres, :brutto_belop, :status, :registrert_arbeidssoker, :fom, :tom, :original_id, :rapporteringstype)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
                mapOf(
                    "id" to rapporteringsperiode.id,
                    "type" to rapporteringsperiode.type.toAdapterKortType(),
                    "ident" to ident,
                    "kan_sendes" to rapporteringsperiode.kanSendes,
                    "kan_sendes_fra" to rapporteringsperiode.kanSendesFra,
                    "kan_endres" to rapporteringsperiode.kanEndres,
                    "brutto_belop" to rapporteringsperiode.bruttoBelop,
                    "status" to rapporteringsperiode.status.name,
                    "registrert_arbeidssoker" to
                        if (rapporteringsperiode.type == KortType.Etterregistrert) {
                            true
                        } else {
                            rapporteringsperiode.registrertArbeidssoker
                        },
                    "fom" to rapporteringsperiode.periode.fraOgMed,
                    "tom" to rapporteringsperiode.periode.tilOgMed,
                    "original_id" to rapporteringsperiode.originalId,
                    "rapporteringstype" to rapporteringsperiode.rapporteringstype,
                ),
            ).asUpdate,
        )

    private fun TransactionalSession.lagreDager(
        rapporteringId: String,
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

    override suspend fun slettOgLagreAktiviteter(
        rapporteringId: String,
        dagId: UUID,
        dag: Dag,
    ) = actionTimer.timedAction("db-lagreAktiviteter") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            "DELETE FROM aktivitet WHERE dag_id = ?",
                            dagId,
                        ).asUpdate,
                    )

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
        rapporteringId: String,
        ident: String,
        registrertArbeidssoker: Boolean,
    ) = actionTimer.timedAction("db-oppdaterRegistrertArbeidssoker") {
        sessionOf(dataSource).use { session ->
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
    ) = actionTimer.timedAction("db-oppdaterRapporteringsperiodeFraArena") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET kan_sendes_fra = :kan_sendes_fra,
                                kan_sendes = :kan_sendes,
                                kan_endres = :kan_endres,
                                brutto_belop = :brutto_belop,
                                status = :status
                            WHERE id = :id
                            """.trimIndent(),
                            mapOf(
                                "kan_sendes_fra" to rapporteringsperiode.kanSendesFra,
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

    override suspend fun oppdaterBegrunnelse(
        rapporteringId: String,
        ident: String,
        begrunnelse: String,
    ) = actionTimer.timedAction("db-oppdaterBegrunnelse") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET begrunnelse_endring = :begrunnelse
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "begrunnelse" to begrunnelse,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun settKanSendes(
        rapporteringId: String,
        ident: String,
        kanSendes: Boolean,
    ) = actionTimer.timedAction("db-settKanSendes") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET kan_sendes = :kanSendes
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "kanSendes" to kanSendes,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun oppdaterRapporteringstype(
        rapporteringId: String,
        ident: String,
        rapporteringstype: String,
    ) = actionTimer.timedAction("db-oppdaterRapporteringstype") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET rapporteringstype = :rapporteringstype
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "rapporteringstype" to rapporteringstype,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ),
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun oppdaterPeriodeEtterInnsending(
        rapporteringId: String,
        ident: String,
        kanEndres: Boolean,
        kanSendes: Boolean,
        status: RapporteringsperiodeStatus,
        oppdaterMottattDato: Boolean,
    ) = actionTimer.timedAction("db-oppdaterPeriodeEtterInnsending") {
        sessionOf(dataSource).use { session ->
            session.transaction { tx ->
                tx
                    .run(
                        queryOf(
                            """
                            UPDATE rapporteringsperiode
                            SET kan_sendes = :kanSendes,
                                kan_endres = :kanEndres,
                                status = :status
                                ${if (oppdaterMottattDato) ",mottatt_dato = :mottattDato" else ""}
                            WHERE id = :id AND ident = :ident
                            """.trimIndent(),
                            mapOf(
                                "kanSendes" to kanSendes,
                                "kanEndres" to kanEndres,
                                "status" to status.name,
                                "id" to rapporteringId,
                                "ident" to ident,
                            ).let {
                                if (oppdaterMottattDato) {
                                    it.plus("mottattDato" to LocalDate.now())
                                } else {
                                    it
                                }
                            },
                        ).asUpdate,
                    ).validateRowsAffected()
            }
        }
    }

    override suspend fun slettAktiviteter(dagId: UUID) =
        actionTimer.timedAction("db-slettAktiviteter") {
            sessionOf(dataSource).use { session ->
                session.transaction { tx ->
                    tx
                        .run(
                            queryOf(
                                "DELETE FROM aktivitet WHERE dag_id = ?",
                                dagId,
                            ).asUpdate,
                        )
                }
            }
        }

    override suspend fun slettRaporteringsperiode(rapporteringId: String) =
        actionTimer.timedAction("db-slettRaporteringsperiode") {
            sessionOf(dataSource).use { session ->
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
        actionTimer.timedAction("db-hentAntallRapporteringsperioder") {
            sessionOf(dataSource).use { session ->
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
        id = string("id"),
        type = (stringOrNull("type") ?: "09").toKortType(),
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
        originalId = stringOrNull("original_id"),
        rapporteringstype = stringOrNull("rapporteringstype"),
        mottattDato = localDateOrNull("mottatt_dato"),
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
