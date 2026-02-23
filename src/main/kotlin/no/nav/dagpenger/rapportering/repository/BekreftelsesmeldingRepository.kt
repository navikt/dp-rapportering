package no.nav.dagpenger.rapportering.repository

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface BekreftelsesmeldingRepository {
    suspend fun lagreBekreftelsesmelding(
        rapporteringsperiodeId: String,
        ident: String,
        skalSendesDato: LocalDate,
    )

    suspend fun hentBekreftelsesmeldingerSomSkalSendes(skalSendesDato: LocalDate): List<Triple<UUID, String, String>>

    suspend fun oppdaterBekreftelsesmelding(
        id: UUID,
        sendtBekreftelseId: UUID,
        sendtTimestamp: LocalDateTime,
    )
}
