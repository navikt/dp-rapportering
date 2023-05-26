package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Rapporteringsperiode
import java.util.UUID

class InMemoryRapporteringsperiodeRepository : RapporteringsperiodeRepository {
    private val rapporteringsperioder = PersonCollection<Rapporteringsperiode>()
    override fun hentRapporteringsperiode(ident: String, uuid: UUID) =
        rapporteringsperioder.hent(ident).find { it.rapporteringsperiodeId == uuid }

    override fun hentRapporteringsperioder(ident: String) =
        rapporteringsperioder.hent(ident)

    override fun lagreRapporteringsperiode(ident: String, rapporteringsperiode: Rapporteringsperiode) =
        rapporteringsperioder.hent(ident).add(rapporteringsperiode)
}
