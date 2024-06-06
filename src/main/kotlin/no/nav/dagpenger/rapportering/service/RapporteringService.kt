package no.nav.dagpenger.rapportering.service

import no.nav.dagpenger.rapportering.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.model.Rapporteringsperiode
import no.nav.dagpenger.rapportering.repository.RapporteringRepository

class RapporteringService(
    val meldepliktConnector: MeldepliktConnector,
    val rapporteringRepository: RapporteringRepository,
) {
    suspend fun hentGjeldendePeriode(
        ident: String,
        token: String,
    ): Rapporteringsperiode? =
        meldepliktConnector
            .hentRapporteringsperioder(ident, token)
            .minByOrNull { it.periode.fraOgMed }
            ?.let { gjeldendePeriode ->
                gjeldendePeriode
            }
}
