package no.nav.dagpenger.rapportering.api.dto

import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInnerDTO
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.util.SortedSet
import java.util.UUID
import kotlin.time.Duration

internal class RapporteringsperiodeMapper(rapporteringsperiode: Rapporteringsperiode, private var id: UUID? = null) :
    RapporteringsperiodVisitor {
    private lateinit var beregnesEtter: LocalDate
    private lateinit var periode: ClosedRange<LocalDate>
    private lateinit var tilstand: Rapporteringsperiode.TilstandType
    private val dager: SortedSet<Dag> = sortedSetOf(Dag.eldsteDagFørst)
    private var korrigerer: UUID? = null
    private var korrigertAv: UUID? = null
    val dto: RapporteringsperiodeDTO
        get() = RapporteringsperiodeDTO(
            id = id,
            beregnesEtter = beregnesEtter,
            fraOgMed = periode.start,
            tilOgMed = periode.endInclusive,
            status = when (tilstand) {
                TilUtfylling -> RapporteringsperiodeDTO.Status.TilUtfylling
                Rapporteringsperiode.TilstandType.Godkjent -> RapporteringsperiodeDTO.Status.Godkjent
                Rapporteringsperiode.TilstandType.Innsendt -> RapporteringsperiodeDTO.Status.Innsendt
            },
            dager = dager.mapIndexed { index, it -> DagMapper(index, it).dto },
            korrigerer = korrigerer,
            korrigertAv = korrigertAv,
        )

    init {
        rapporteringsperiode.accept(this)
    }

    override fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
        beregnesEtter: LocalDate,
        korrigerer: Rapporteringsperiode?,
        korrigertAv: Rapporteringsperiode?,
    ) {
        if (this.id != null && this.id != id) return
        if (korrigerer != null && tilstand == TilUtfylling && this.id == null) return
        this.id = id
        this.beregnesEtter = beregnesEtter
        this.periode = periode
        this.tilstand = tilstand
        this.korrigerer = korrigerer?.rapporteringsperiodeId
        this.korrigertAv = korrigertAv?.rapporteringsperiodeId
    }

    override fun visit(
        dag: Dag,
        dato: LocalDate,
        aktiviteter: List<Aktivitet>,
        muligeAktiviter: List<Aktivitet.AktivitetType>,
        strategi: Dag.StrategiType,
    ) {
        if (this.id == null) return
        // Legg til dager i et sortet set for å garantere rekkefølge
        this.dager.add(dag)
    }

    private class DagMapper(private val index: Int, dag: Dag) :
        DagVisitor {
        lateinit var dto: RapporteringsperiodeDagerInnerDTO

        init {
            dag.accept(this)
        }

        override fun visit(
            dag: Dag,
            dato: LocalDate,
            aktiviteter: List<Aktivitet>,
            muligeAktiviter: List<Aktivitet.AktivitetType>,
            strategi: Dag.StrategiType,
        ) {
            dto = RapporteringsperiodeDagerInnerDTO(
                dagIndex = index,
                dato = dato,
                muligeAktiviteter = muligeAktiviter.map {
                    AktivitetTypeDTO.valueOf(
                        it.name,
                    )
                },
                aktiviteter = aktiviteter.tilDto(),
            )
        }

        private fun List<Aktivitet>.tilDto() = this.map {
            AktivitetMapper(it).aktivitetDTO
        }

        private class AktivitetMapper(aktivitet: Aktivitet) : AktivitetVisitor {
            lateinit var aktivitetDTO: AktivitetDTO

            init {
                aktivitet.accept(this)
            }

            override fun visit(
                aktivitet: Aktivitet,
                uuid: UUID,
                dato: LocalDate,
                tid: Duration,
                type: Aktivitet.AktivitetType,
                tilstand: Aktivitet.TilstandType,
            ) {
                aktivitetDTO = AktivitetDTO(
                    type = AktivitetTypeDTO.valueOf(type.name),
                    dato = dato,
                    id = uuid,
                    timer = tid.toIsoString(),
                )
            }
        }
    }
}
