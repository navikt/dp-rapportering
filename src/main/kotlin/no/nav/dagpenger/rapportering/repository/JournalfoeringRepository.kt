package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.MidlertidigLagretData

interface JournalfoeringRepository {
    fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: Long,
    )

    fun lagreDataMidlertidig(midlertidigLagretData: MidlertidigLagretData)

    fun hentMidlertidigLagretData(): List<Triple<String, MidlertidigLagretData, Int>>

    fun sletteMidlertidigLagretData(id: String)

    fun oppdaterMidlertidigLagretData(
        id: String,
        retries: Int,
    )

    fun hentAntallJournalposter(): Int

    fun hentAntallMidlertidigLagretData(): Int
}
