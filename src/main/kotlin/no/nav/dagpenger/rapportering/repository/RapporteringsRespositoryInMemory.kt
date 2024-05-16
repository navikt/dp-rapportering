package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Rapporteringsperiode

class RapporteringsRespositoryInMemory : RapporteringsRepository {
    override fun hentRapporteringsperioder(ident: String) = emptyList<Rapporteringsperiode>()
}
