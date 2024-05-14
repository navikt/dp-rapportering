package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO

interface RapporteringsRepository {
    fun hentRapporteringsperioder(ident: String): List<RapporteringsperiodeDTO>
}
