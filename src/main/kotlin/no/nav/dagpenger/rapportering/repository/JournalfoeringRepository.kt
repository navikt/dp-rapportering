package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.MidlertidigLagretData

interface JournalfoeringRepository {
    suspend fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: Long,
    )

    suspend fun lagreDataMidlertidig(midlertidigLagretData: MidlertidigLagretData)

    suspend fun hentMidlertidigLagretData(): List<Triple<String, MidlertidigLagretData, Int>>

    suspend fun sletteMidlertidigLagretData(id: String)

    suspend fun oppdaterMidlertidigLagretData(
        id: String,
        retries: Int,
    )

    suspend fun hentAntallJournalposter(): Int

    suspend fun hentAntallMidlertidigLagretData(): Int
}
