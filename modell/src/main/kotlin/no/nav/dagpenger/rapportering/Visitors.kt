package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

interface RapporteringsperiodVisitor : AktivitetstidslinjeVisitor {
    fun visit(id: UUID, periode: ClosedRange<LocalDate>, tilstand: Rapporteringsperiode.TilstandType) {}
}

interface AktivitetstidslinjeVisitor {
    fun visit(aktiviteter: List<Aktivitet>) {}
}

interface AktivitetVisitor {
    fun visit(
        dato: LocalDate,
        tid: Duration,
        type: Aktivitet.AktivitetType,
        uuid: UUID = UUID.randomUUID(),
    ) {
    }
}
