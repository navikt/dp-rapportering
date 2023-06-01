package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.Dag
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Opprettet
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInnerDTO
import no.nav.dagpenger.rapportering.repository.AktivitetRepository
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.DurationUnit

fun Application.rapporteringApi(
    rapporteringsperiodeRepository: RapporteringsperiodeRepository,
    aktivitetRepository: AktivitetRepository,
) {
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(call.ident())
                        .map { RapporteringsperiodeMapper(it).dto }

                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                route("{id}") {
                    get {
                        val dto =
                            rapporteringsperiodeRepository.hentRapporteringsperiode(call.ident(), call.finnUUID("id"))
                                ?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

                        call.respond(HttpStatusCode.OK, dto)
                    }

                    route("/godkjenn") {
                        post {
                            val id = call.parameters["id"]?.let {
                                UUID.fromString(it)
                            }
                            val dto = rapporteringsperiodeRepository.hentRapporteringsperiode(
                                call.ident(),
                                call.finnUUID("id"),
                            )?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")
                            val godkjent = dto.copy(status = RapporteringsperiodeDTO.Status.Godkjent)

                            call.respond(HttpStatusCode.Created, godkjent)
                        }
                    }
                }
            }
        }
    }
}

private class RapporteringsperiodeMapper(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
    private val dager = mutableMapOf<LocalDate, List<Aktivitet.AktivitetType>>()
    lateinit var id: UUID
    lateinit var periode: ClosedRange<LocalDate>
    lateinit var tilstand: Rapporteringsperiode.TilstandType
    val aktiviteter: MutableList<Aktivitet> = mutableListOf()
    val dto: RapporteringsperiodeDTO
        get() {
            return RapporteringsperiodeDTO(
                id = id,
                fraOgMed = periode.start,
                tilOgMed = periode.endInclusive,
                status = when (tilstand) {
                    Opprettet -> RapporteringsperiodeDTO.Status.TilUtfylling
                    Godkjent -> RapporteringsperiodeDTO.Status.Godkjent
                    Innsendt -> RapporteringsperiodeDTO.Status.Innsendt
                },
                dager = lagRapporteringsdager(),
                aktiviteter = aktiviteter.tilDto(),
            )
        }

    init {
        rapporteringsperiode.accept(this)
    }

    private fun lagRapporteringsdager(): List<RapporteringsperiodeDagerInnerDTO> {
        return mutableListOf<RapporteringsperiodeDagerInnerDTO>().apply {
            dager.onEachIndexed { index, dag ->
                add(
                    RapporteringsperiodeDagerInnerDTO(
                        dagIndex = index,
                        dato = dag.key,
                        muligeAktiviteter = dag.value.map { AktivitetTypeDTO.valueOf(it.name) },
                    ),
                )
            }
        }.toList()
    }

    class AktivitetMapper(aktivitet: Aktivitet) : AktivitetVisitor {
        lateinit var aktivitetDTO: AktivitetDTO

        init {
            aktivitet.accept(this)
        }

        override fun visit(
            aktivitet: Aktivitet,
            dato: LocalDate,
            tid: Duration,
            type: Aktivitet.AktivitetType,
            uuid: UUID,
        ) {
            aktivitetDTO = AktivitetDTO(
                type = AktivitetTypeDTO.valueOf(type.name),
                dato = dato,
                id = uuid,
                timer = tid.toDouble(DurationUnit.HOURS).toBigDecimal(),
            )
        }
    }

    private fun List<Aktivitet>.tilDto() = this.map {
        AktivitetMapper(it).aktivitetDTO
    }

    override fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
    ) {
        this.id = id
        this.periode = periode
        this.tilstand = tilstand
    }

    override fun visit(
        dag: Dag,
        dato: LocalDate,
        aktiviteter: List<Aktivitet>,
        muligeAktiviter: List<Aktivitet.AktivitetType>,
    ) {
        this.dager[dato] = muligeAktiviter
        this.aktiviteter += aktiviteter
    }
}
