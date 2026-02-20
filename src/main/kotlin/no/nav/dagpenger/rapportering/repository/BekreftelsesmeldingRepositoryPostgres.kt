package no.nav.dagpenger.rapportering.repository

import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.dagpenger.rapportering.metrics.ActionTimer
import no.nav.dagpenger.rapportering.utils.RepositoryUtils.validateRowsAffected
import no.nav.dagpenger.rapportering.utils.UUIDv7
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class BekreftelsesmeldingRepositoryPostgres(
    private val dataSource: DataSource,
    private val actionTimer: ActionTimer,
) : BekreftelsesmeldingRepository {
    override suspend fun lagreBekreftelsesmelding(
        rapporteringsperiodeId: String,
        ident: String,
        skalSendesDato: LocalDate,
    ) = actionTimer.timedAction("db-lagreBekreftelsesmelding") {
        sessionOf(dataSource).use { session ->
            session
                .run(
                    queryOf(
                        "INSERT INTO bekreftelsesmelding (id, rapporteringsperiode_id, ident, skal_sendes_dato) " +
                            "VALUES (?, ?, ?, ?)",
                        UUIDv7.newUuid().toString(),
                        rapporteringsperiodeId,
                        ident,
                        skalSendesDato,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }

    override suspend fun hentBekreftelsesmeldingerSomSkalSendes(skalSendesDato: LocalDate): List<Triple<UUID, String, String>> =
        actionTimer.timedAction("db-hentBekreftelsesmeldinger") {
            sessionOf(dataSource).use { session ->
                session.run(
                    queryOf(
                        "SELECT id, rapporteringsperiode_id, ident FROM bekreftelsesmelding WHERE skal_sendes_dato = ? AND sendt_timestamp IS NULL",
                        skalSendesDato,
                    ).map {
                        Triple(
                            UUIDv7.fromString(it.string("id")),
                            it.string("rapporteringsperiode_id"),
                            it.string("ident"),
                        )
                    }.asList,
                )
            }
        }

    override suspend fun oppdaterBekreftelsesmelding(
        id: UUID,
        sendtBekreftelseId: UUID,
        sendtTimestamp: LocalDateTime,
    ) = actionTimer.timedAction("db-oppdaterBekreftelsesmelding") {
        sessionOf(dataSource).use { session ->
            session
                .run(
                    queryOf(
                        "UPDATE bekreftelsesmelding SET sendt_bekreftelse_id = ?, sendt_timestamp = ? WHERE id = ?",
                        sendtBekreftelseId,
                        sendtTimestamp,
                        id,
                    ).asUpdate,
                ).validateRowsAffected()
        }
    }
}
