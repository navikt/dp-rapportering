package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration

interface PersonVisitor : RapporteringsperiodVisitor, AktivitetstidslinjeVisitor {
    fun visit(person: Person, ident: String) {}
}

interface RapporteringsperiodVisitor : AktivitetstidslinjeVisitor {
    fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
    ) {
    }
}

interface AktivitetstidslinjeVisitor : AktivitetVisitor {
    fun visit(aktiviteter: List<Aktivitet>) {}
}

interface AktivitetVisitor {
    fun visit(
        aktivitet: Aktivitet,
        dato: LocalDate,
        tid: Duration,
        type: Aktivitet.AktivitetType,
        uuid: UUID = UUID.randomUUID(),
    ) {
    }
}
