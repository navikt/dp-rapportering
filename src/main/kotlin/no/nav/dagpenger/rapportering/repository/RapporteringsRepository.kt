package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Rapporteringsperiode

interface RapporteringsRepository {
    fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode>
}
