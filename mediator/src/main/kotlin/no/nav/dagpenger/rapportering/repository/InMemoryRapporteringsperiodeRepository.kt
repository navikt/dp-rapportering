package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Rapporteringsperiode
import java.time.LocalDate
import java.util.UUID

internal class InMemoryRapporteringsperiodeRepository(
    val testData: PersonCollection<Rapporteringsperiode>.() -> Unit = {},
) :
    RapporteringsperiodeRepository {
    private val rapporteringsperioder = PersonCollection<Rapporteringsperiode>().also {
        it.testData()
    }

    override fun hentRapporteringsperiode(ident: String, uuid: UUID) =
        rapporteringsperioder.hent(ident).find { it.rapporteringsperiodeId == uuid }
            ?: Rapporteringsperiode(LocalDate.now().minusDays(5)).also {
                rapporteringsperioder.hent(ident).add(it)
            }

    override fun hentRapporteringsperioder(ident: String) =
        rapporteringsperioder.hent(ident)

    override fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate): Rapporteringsperiode? {
        return rapporteringsperioder.hent(ident).find { it.gjelderFor(dato) }
    }
}
