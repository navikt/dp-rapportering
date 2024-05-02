package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.modeller.AktivitetType
import no.nav.dagpenger.rapportering.modeller.Rapporteringsperiode
import no.nav.dagpenger.rapportering.modeller.RapporteringsperiodeDag
import no.nav.dagpenger.rapportering.modeller.Rapporteringsperiodetilstand
import java.time.LocalDate
import java.util.UUID

class RapporteringsRespositoryInMemory : RapporteringsRepository {
    override fun hentRapporteringsperioder(ident: String): List<Rapporteringsperiode> =
        listOf(
            Rapporteringsperiode(
                id = UUID.randomUUID(),
                beregnesEtter = LocalDate.now(),
                fraOgMed = LocalDate.now(),
                tilOgMed = LocalDate.now(),
                status = Rapporteringsperiodetilstand.TilUtfylling,
                dager =
                    (0..13).map { dagIndex ->
                        RapporteringsperiodeDag(
                            dagIndex = dagIndex,
                            dato = LocalDate.now(),
                            muligeAktiviteter = AktivitetType.entries.map { it },
                            aktiviteter = emptyList(),
                        )
                    },
            ),
        )
}
