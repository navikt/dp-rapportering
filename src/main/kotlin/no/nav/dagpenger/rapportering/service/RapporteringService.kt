package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.model.Dag
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.repository.RapporteringRepository
import java.util.UUID

class RapporteringService(
    private val meldepliktConnector: MeldepliktConnector,
    private val rapporteringRepository: RapporteringRepository,
) {
    suspend fun hentGjeldendePeriode(
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            .minByOrNull { it.periode.fraOgMed }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }

    suspend fun hentPeriode(
        rapporteringId: Long,
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            .firstOrNull { it.id == rapporteringId }
            ?.let { lagreEllerOppdaterPeriode(it, ident) }

    private fun lagreEllerOppdaterPeriode(
        periode: Rapporteringsperiode,
        ident: String,
    ): Rapporteringsperiode =
        if (rapporteringRepository.hentRapporteringsperiode(periode.id, ident) == null) {
            rapporteringRepository.lagreRapporteringsperiodeOgDager(periode, ident)
            periode
        } else {
            rapporteringRepository.oppdaterRapporteringsperiodeFraArena(periode, ident)
            rapporteringRepository.hentRapporteringsperiode(periode.id, ident)
                ?: throw RuntimeException("Fant ikke rapporteringsperiode, selv om den skal ha blitt lagret")
        }

    fun lagreAktiviteter(
        rapporteringId: Long,
        dag: Dag,
    ) = rapporteringRepository.lagreAktiviteter(rapporteringId, dag)

    fun slettAktivitet(aktivitetId: UUID) = rapporteringRepository.slettAktivitet(aktivitetId)
}
