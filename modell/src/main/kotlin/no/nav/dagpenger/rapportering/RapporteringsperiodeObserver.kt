package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.util.UUID

interface PersonObserver : RapporteringsperiodeObserver
interface RapporteringsperiodeObserver {
    fun rapporteringsperiodeEndret(event: RapporteringsperiodeEndret)
    fun rapporteringsperiodeInnsendt(event: RapporteringsperiodeInnsendt)

    data class RapporteringsperiodeEndret(
        // val ident: String,
        val rapporteringsperiodeId: UUID,
        val gjeldendeTilstand: TilstandType,
        val forrigeTilstand: TilstandType,
        val fom: LocalDate,
        val tom: LocalDate,
    )

    data class RapporteringsperiodeInnsendt(
        val rapporteringsperiodeId: UUID,
        val fom: LocalDate,
        val tom: LocalDate,
        val dager: List<Dag>,
        val sakId: UUID,
        val korrigerer: UUID?,
    )
}
