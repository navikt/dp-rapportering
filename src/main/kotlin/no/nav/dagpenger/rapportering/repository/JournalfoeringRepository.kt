package no.nav.dagpenger.rapportering.repository

import no.nav.sbl.meldekort.model.meldekort.journalpost.Journalpost

interface JournalfoeringRepository {
    fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        meldekortId: Long,
    )

    fun lagreJournalpostMidlertidig(journalpost: Journalpost)

    fun hentMidlertidigLagredeJournalposter(): List<Triple<String, Journalpost, Int>>

    fun hentJournalpostData(journalpostId: Long): List<Triple<Long, Long, Long>>

    fun sletteMidlertidigLagretJournalpost(id: String)

    fun oppdaterMidlertidigLagretJournalpost(
        id: String,
        retries: Int,
    )
}
