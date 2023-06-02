package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.tidslinje.Dag
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
        rapporteringsfrist: LocalDate,
    ) {
    }
}

interface AktivitetstidslinjeVisitor : DagVisitor {
    fun preVisit(aktivitetstidslinje: Aktivitetstidslinje) {}
    fun postVisit(aktivitetstidslinje: Aktivitetstidslinje) {}
}

interface DagVisitor : AktivitetVisitor {
    fun visit(
        dag: Dag,
        dato: LocalDate,
        aktiviteter: List<Aktivitet>,
        muligeAktiviter: List<Aktivitet.AktivitetType>,
    ) {
    }
}

interface AktivitetVisitor {
    fun visit(
        aktivitet: Aktivitet,
        uuid: UUID = UUID.randomUUID(),
        dato: LocalDate,
        tid: Duration,
        type: Aktivitet.AktivitetType,
        tilstand: Aktivitet.TilstandType,
    ) {
    }
}
