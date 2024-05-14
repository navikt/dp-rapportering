package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.modeller.Rapporteringsperiode

class RapporteringsRespositoryInMemory : RapporteringsRepository {
    override fun hentRapporteringsperioder(ident: String) = emptyList<Rapporteringsperiode>()
}
