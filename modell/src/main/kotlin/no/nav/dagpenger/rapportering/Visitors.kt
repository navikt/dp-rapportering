package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
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

interface AktivitetstidslinjeVisitor : AktivitetVisitor, DagVisitor {
    fun preVisit(aktivitetstidslinje: Aktivitetstidslinje) {}
    fun postVisit(aktivitetstidslinje: Aktivitetstidslinje) {}
}

interface DagVisitor {
    fun visit(dag: Dag, dato: LocalDate, aktiviteter: List<Aktivitet>, muligeAktiviter: List<Aktivitet>) {}
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
