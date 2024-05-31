package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.model.Rapporteringsperiode

interface RapporteringRepository {
    fun hentRapporteringsperiode(
        id: Long,
        ident: String,
    ): Rapporteringsperiode?

    fun hentRapporteringsperioder(): List<Rapporteringsperiode>

    fun lagreRapporteringsperiode(
        rapporteringsperiode: Rapporteringsperiode,
        ident: String,
    )
}
