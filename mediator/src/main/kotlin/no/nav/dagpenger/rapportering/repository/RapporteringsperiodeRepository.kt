package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Rapporteringsperiode
import java.time.LocalDate
import java.util.UUID

interface RapporteringsperiodeRepository {
    fun hentRapporteringsperiode(ident: String, uuid: UUID): Rapporteringsperiode?
    fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode>
    fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate): Rapporteringsperiode?
    fun finnIdentForPeriode(finnUUID: UUID): String?
}
