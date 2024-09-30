package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Journalpost
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode

interface JournalfoeringRepository {
    fun lagreJournalpostData(
        journalpostId: Long,
        dokumentInfoId: Long,
        rapporteringsperiodeId: Long,
    )

    fun lagreJournalpostMidlertidig(rapporteringsperiode: Rapporteringsperiode)

    fun hentMidlertidigLagredeJournalposter(): List<Triple<String, Journalpost, Int>>

    fun hentJournalpostData(journalpostId: Long): List<Triple<Long, Long, Long>>

    fun sletteMidlertidigLagretJournalpost(id: String)

    fun oppdaterMidlertidigLagretJournalpost(
        id: String,
        retries: Int,
    )

    fun hentAntallJournalposter(): Int

    fun hentAntallMidlertidligeJournalposter(): Int
}
