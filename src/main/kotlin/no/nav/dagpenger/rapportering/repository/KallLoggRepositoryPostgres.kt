package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.model.KallLogg
import java.time.Instant
import javax.sql.DataSource

class KallLoggRepositoryPostgres(
    private val dataSource: DataSource,
) : KallLoggRepository {
    override fun lagreKallLogg(kallLogg: KallLogg): Long =
        using(sessionOf(dataSource, true)) { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO kall_logg " +
                            "(korrelasjon_id, type, tidspunkt, kall_retning, method, " +
                            "operation, status, kalltid, request, response, ident, logginfo) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        kallLogg.korrelasjonId,
                        kallLogg.type,
                        kallLogg.tidspunkt,
                        kallLogg.kallRetning,
                        kallLogg.method,
                        kallLogg.operation,
                        kallLogg.status,
                        kallLogg.kallTid,
                        kallLogg.request,
                        kallLogg.response,
                        kallLogg.ident,
                        kallLogg.logginfo,
                    ).asUpdateAndReturnGeneratedKey,
                ) ?: 0L
        }

    override fun lagreResponse(
        kallLoggId: Long,
        status: Int,
        response: String,
    ) {
        using(sessionOf(dataSource)) { session ->
            session
                .run(
                    queryOf(
                        "UPDATE kall_logg " +
                            "SET response = ?, status = ?, kalltid = (? - kalltid) " +
                            "WHERE kall_logg_id = ?",
                        response,
                        status,
                        Instant.now().toEpochMilli(),
                        kallLoggId,
                    ).asUpdate,
                )
        }
    }

    override fun hentKallLoggFelterListeByKorrelasjonId(korrelasjonId: String): List<KallLogg> =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT " +
                        "korrelasjon_id, tidspunkt, type, kall_retning, method, operation, status, kalltid, ident, " +
                        "convert_from(lo_get(request::oid), 'UTF8') as request," +
                        "convert_from(lo_get(response::oid), 'UTF8') as response," +
                        "convert_from(lo_get(logginfo::oid), 'UTF8') as logginfo " +
                        "FROM kall_logg " +
                        "WHERE korrelasjon_id = ?",
                    korrelasjonId,
                ).map {
                    KallLogg(
                        it.string("korrelasjon_id"),
                        it.localDateTime("tidspunkt"),
                        it.string("type"),
                        it.string("kall_retning"),
                        it.string("method"),
                        it.string("operation"),
                        it.int("status"),
                        it.long("kalltid"),
                        it.string("request"),
                        it.string("response"),
                        it.string("ident"),
                        it.string("logginfo"),
                    )
                }.asList,
            )
        }
}
