package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.util.UUID

interface RapporteringRepository {
    suspend fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode?

    suspend fun hentLagredeRapporteringsperioder(ident: String): List<Rapporteringsperiode>

    suspend fun hentAlleLagredeRapporteringsperioder(): List<Rapporteringsperiode>

    suspend fun hentDagId(
        rapporteringId: Long,
        dagIdex: Int,
    ): UUID

    suspend fun hentAktiviteter(dagId: UUID): List<Aktivitet>

    suspend fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    suspend fun lagreAktiviteter(
        rapporteringId: Long,
        dagId: UUID,
        dag: Dag,
    )

    suspend fun oppdaterRegistrertArbeidssoker(
        rapporteringId: Long,
        ident: String,
        registrertArbeidssoker: Boolean,
    )

    suspend fun oppdaterRapporteringsperiodeFraArena(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    suspend fun oppdaterRapporteringStatus(
        rapporteringId: Long,
        ident: String,
        status: RapporteringsperiodeStatus,
    )

    suspend fun slettAktiviteter(aktivitetIdListe: List<UUID>)

    suspend fun slettRaporteringsperiode(rapporteringId: Long)

    suspend fun hentAntallRapporteringsperioder(): Int
}
