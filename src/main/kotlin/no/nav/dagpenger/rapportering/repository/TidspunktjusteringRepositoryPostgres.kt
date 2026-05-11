package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import javax.sql.DataSource

class TidspunktjusteringRepositoryPostgres(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : TidspunktjusteringRepository {
    override suspend fun hentInnsendingtidspunkt(periodeKode: String): Int? =
        actionTimer.timedAction("db-hentInnsendingtidspunkt") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT VERDI FROM INNSENDINGTIDSPUNKT WHERE PERIODE_KODE = ?",
                        periodeKode,
                    ).map { it.int("VERDI") }.asSingle,
                )
            }
        }

    override suspend fun hentSisteFristForTrekkJustering(periodeKode: String): Long? =
        actionTimer.timedAction("db-hentSisteFristForTrekkJustering") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT verdi FROM siste_frist_for_trekk_justeringer WHERE periode_kode = ?",
                        periodeKode,
                    ).map { it.long("verdi") }.asSingle,
                )
            }
        }
}
