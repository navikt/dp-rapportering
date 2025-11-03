package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import javax.sql.DataSource

class InnsendingtidspunktRepositoryPostgres(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : InnsendingtidspunktRepository {
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
}
