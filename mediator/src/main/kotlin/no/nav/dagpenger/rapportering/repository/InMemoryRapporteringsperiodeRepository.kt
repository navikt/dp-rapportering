package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Rapporteringsperiode
import java.time.LocalDate
import java.util.UUID

class InMemoryRapporteringsperiodeRepository(private val aktivitetRepository: InMemoryAktivitetRepository) :
    RapporteringsperiodeRepository,
    AktivitetRepository by aktivitetRepository {
    //    ?: RapporteringsperiodeDTO(
//    id = UUID.randomUUID(),
//    fraOgMed = LocalDate.of(2023, 5, 22),
//    tilOgMed = LocalDate.of(2023, 6, 4),
//    status = RapporteringsperiodeDTO.Status.TilUtfylling,
//    dager = lagNoe(),
//    aktiviteter = listOf(),
//    )
    private val rapporteringsperioder = PersonCollection<Rapporteringsperiode>()
    override fun hentRapporteringsperiode(ident: String, uuid: UUID) =
        rapporteringsperioder.hent(ident).find { it.rapporteringsperiodeId == uuid }
            ?: Rapporteringsperiode(LocalDate.now().minusDays(5)).also {
                lagreRapporteringsperiode(ident, it)
            }

    override fun hentRapporteringsperioder(ident: String) =
        rapporteringsperioder.hent(ident)

    override fun lagreRapporteringsperiode(ident: String, rapporteringsperiode: Rapporteringsperiode) =
        rapporteringsperioder.hent(ident).add(rapporteringsperiode)

    override fun hentRapporteringsperiodeFor(ident: String, dato: LocalDate): Rapporteringsperiode? {
        return rapporteringsperioder.hent(ident).find { it.gjelderFor(dato) }
    }
}
