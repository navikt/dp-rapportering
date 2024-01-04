package no.nav.dagpenger.rapportering

import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitetstidslinje
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration

interface PersonVisitor : RapporteringsperiodVisitor, AktivitetstidslinjeVisitor, RapporteringspliktVisitor {
    fun visit(
        person: Person,
        ident: String,
    ) {}
}

interface RapporteringsperiodVisitor : AktivitetstidslinjeVisitor, GodkjenningsloggVisitor {
    fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
        beregnesEtter: LocalDate,
        korrigerer: Rapporteringsperiode?,
        korrigertAv: Rapporteringsperiode?,
    ) {
    }
}

interface GodkjenningsloggVisitor {
    fun visit(
        godkjenningsendring: Godkjenningsendring,
        id: UUID,
        utførtAv: Godkjenningsendring.Kilde,
        opprettet: LocalDateTime,
        avgodkjent: Godkjenningsendring?,
        begrunnelse: String?,
    ) {
    }
}

interface RapporteringspliktVisitor : AktivitetstidslinjeVisitor, TemporalCollectionVisitor<Rapporteringsplikt> {
    fun visit(
        rapporteringsplikt: Rapporteringsplikt,
        id: UUID,
        type: RapporteringspliktType,
    ) {}

    override fun visit(
        at: LocalDateTime,
        item: Rapporteringsplikt,
    ) {
        item.accept(this)
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
        strategi: Dag.StrategiType,
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

interface TemporalCollectionVisitor<R> {
    fun visit(
        at: LocalDateTime,
        item: R,
    )
}
