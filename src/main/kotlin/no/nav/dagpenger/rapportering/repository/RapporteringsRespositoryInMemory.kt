package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagDTO
import java.time.LocalDate
import java.util.UUID

class RapporteringsRespositoryInMemory : RapporteringsRepository {
    override fun hentRapporteringsperioder(ident: String): List<RapporteringsperiodeDTO> =
        listOf(
            RapporteringsperiodeDTO(
                id = UUID.randomUUID(),
                beregnesEtter = LocalDate.now(),
                fraOgMed = LocalDate.now(),
                tilOgMed = LocalDate.now(),
                status = RapporteringsperiodeDTO.Status.TilUtfylling,
                dager =
                    (0..13).map { dagIndex ->
                        RapporteringsperiodeDagDTO(
                            dagIndex = dagIndex,
                            dato = LocalDate.now(),
                            muligeAktiviteter = AktivitetTypeDTO.entries.map { it },
                            aktiviteter = emptyList(),
                        )
                    },
            ),
        )
}
