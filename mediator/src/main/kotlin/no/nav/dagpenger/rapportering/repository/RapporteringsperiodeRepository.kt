package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Rapporteringsperiode
import java.time.LocalDate
import java.util.UUID

interface RapporteringsperiodeRepository : AktivitetRepository {
    fun hentRapporteringsperiode(ident: String, uuid: UUID): Rapporteringsperiode?
    fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode>
    fun lagreRapporteringsperiode(ident: String, rapporteringsperiode: Rapporteringsperiode): Boolean
    fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate): Rapporteringsperiode?
}
