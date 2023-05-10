package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType
import java.time.LocalDate
import java.util.UUID

interface PersonObserver : RapporteringsperiodeObserver
interface RapporteringsperiodeObserver {
    fun rapporteringsperiodeEndret(event: RapporteringsperiodeEndret)

    data class RapporteringsperiodeEndret(
        // val ident: String,
        val rapporteringsperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val forrigeTilstand: TilstandType,
        val fom: LocalDate,
        val tom: LocalDate,
    )
}
