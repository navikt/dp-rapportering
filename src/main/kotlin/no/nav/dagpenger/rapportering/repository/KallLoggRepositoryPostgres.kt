package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.model.KallLogg
import java.time.Instant
import javax.sql.DataSource

class KallLoggRepositoryPostgres(
    private val dataSource: DataSource,
) : KallLoggRepository {
    override fun lagreKallLogg(kallLogg: KallLogg): Long =
        sessionOf(dataSource, true).use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO kall_logg " +
                            "(korrelasjon_id, type, tidspunkt, kall_retning, method, " +
                            "operation, status, kalltid, request, response, ident, logginfo) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        kallLogg.korrelasjonId.replace("\u0000", ""),
                        kallLogg.type.replace("\u0000", ""),
                        kallLogg.tidspunkt,
                        kallLogg.kallRetning.replace("\u0000", ""),
                        kallLogg.method.replace("\u0000", ""),
                        kallLogg.operation.replace("\u0000", ""),
                        kallLogg.status,
                        kallLogg.kallTid,
                        kallLogg.request.replace("\u0000", ""),
                        kallLogg.response.replace("\u0000", ""),
                        kallLogg.ident.replace("\u0000", ""),
                        kallLogg.logginfo.replace("\u0000", ""),
                    ).asUpdateAndReturnGeneratedKey,
                ) ?: 0L
        }

    override fun lagreRequest(
        kallLoggId: Long,
        request: String,
    ) {
        sessionOf(dataSource).use { session ->
            session
                .run(
                    queryOf(
                        "UPDATE kall_logg " +
                            "SET request = ?, kalltid = ? " +
                            "WHERE kall_logg_id = ?",
                        request,
                        Instant.now().toEpochMilli(),
                        kallLoggId,
                    ).asUpdate,
                )
        }
    }

    override fun lagreResponse(
        kallLoggId: Long,
        status: Int,
        response: String,
    ) {
        sessionOf(dataSource).use { session ->
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
        sessionOf(dataSource).use { session ->
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
