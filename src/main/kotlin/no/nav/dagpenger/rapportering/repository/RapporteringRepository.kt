package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Aktivitet
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.model.RapporteringsperiodeStatus
import java.util.UUID

interface RapporteringRepository {
    suspend fun hentRapporteringsperiode(
        id: String,
        ident: String,
    ): Rapporteringsperiode?

    suspend fun finnesRapporteringsperiode(
        id: String,
        ident: String,
    ): Boolean

    suspend fun hentRapporteringsperiodeIdForInnsendtePerioder(): List<String>

    suspend fun hentRapporteringsperiodeIdForMidlertidigePerioder(): List<String>

    suspend fun hentRapporteringsperiodeIdForPerioderEtterSisteFrist(): List<String>

    suspend fun hentLagredeRapporteringsperioder(ident: String): List<Rapporteringsperiode>

    suspend fun hentAlleLagredeRapporteringsperioder(): List<Rapporteringsperiode>

    suspend fun hentDagerUtenAktivitet(rapporteringId: String): List<Pair<UUID, Dag>>

    suspend fun hentDagId(
        rapporteringId: String,
        dagIdex: Int,
    ): UUID

    suspend fun hentAktiviteter(dagId: UUID): List<Aktivitet>

    suspend fun hentKanSendes(rapporteringId: String): Boolean?

    suspend fun lagreRapporteringsperiodeOgDager(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    suspend fun slettOgLagreAktiviteter(
        rapporteringId: String,
        dagId: UUID,
        dag: Dag,
    )

    suspend fun oppdaterRegistrertArbeidssoker(
        rapporteringId: String,
        ident: String,
        registrertArbeidssoker: Boolean,
    )

    suspend fun oppdaterRapporteringsperiodeFraArena(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )

    suspend fun oppdaterBegrunnelse(
        rapporteringId: String,
        ident: String,
        begrunnelse: String,
    )

    suspend fun settKanSendes(
        rapporteringId: String,
        ident: String,
        kanSendes: Boolean,
    )

    suspend fun oppdaterRapporteringstype(
        rapporteringId: String,
        ident: String,
        rapporteringstype: String,
    )

    suspend fun oppdaterPeriodeEtterInnsending(
        rapporteringId: String,
        ident: String,
        kanEndres: Boolean,
        kanSendes: Boolean,
        status: RapporteringsperiodeStatus,
        oppdaterMottattDato: Boolean = true,
    )

    suspend fun slettAktiviteter(dagId: UUID): Int

    suspend fun slettRaporteringsperiode(rapporteringId: String)

    suspend fun hentAntallRapporteringsperioder(): Int
}
