package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.DagVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Opprettet
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetInputDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInnerDTO
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.util.SortedSet
import java.util.UUID

fun Application.rapporteringApi(
    rapporteringsperiodeRepository: RapporteringsperiodeRepository,
) {
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(call.ident())
                        .map { RapporteringsperiodeMapper(it).dto }

                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                route("/gjeldende") {
                    get {
                        call.respond(HttpStatusCode.OK)
                    }
                }

                route("{periodeId}") {
                    get {
                        val dto =
                            rapporteringsperiodeRepository.hentRapporteringsperiode(
                                call.ident(),
                                call.finnUUID("periodeId"),
                            )
                                ?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

                        call.respond(HttpStatusCode.OK, dto)
                    }

                    route("/godkjenn") {
                        post {
                            call.parameters["periodeId"]?.let {
                                UUID.fromString(it)
                            }
                            val dto = rapporteringsperiodeRepository.hentRapporteringsperiode(
                                call.ident(),
                                call.finnUUID("periodeId"),
                            )?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")
                            val godkjent = dto.copy(status = RapporteringsperiodeDTO.Status.Godkjent)

                            call.respond(HttpStatusCode.Created, godkjent)
                        }
                    }

                    route("/aktivitet") {
                        get {
                            val aktiviteter = rapporteringsperiodeRepository.hentAktiviteter(call.ident()).map {
                                AktivitetDTO(
                                    id = it.uuid,
                                    dato = it.dato,
                                    type = AktivitetTypeDTO.valueOf(it.type.name),
                                    timer = it.tid.toIsoString(),
                                )
                            }
                            call.respond(HttpStatusCode.OK, aktiviteter)
                        }

                        post {
                            val aktivitetInput = call.receive<AktivitetInputDTO>()
                            val aktivitet = when (aktivitetInput.type) {
                                AktivitetTypeDTO.Arbeid -> Aktivitet.Arbeid(
                                    dato = aktivitetInput.dato,
                                    arbeidstimer = aktivitetInput.timer?.toDouble()
                                        ?: throw IllegalArgumentException("Må ha antall arbeidstimer"),
                                )

                                AktivitetTypeDTO.Syk -> Aktivitet.Syk(
                                    dato = aktivitetInput.dato,
                                )

                                AktivitetTypeDTO.Ferie -> Aktivitet.Ferie(
                                    dato = aktivitetInput.dato,
                                )
                            }

                            rapporteringsperiodeRepository.leggTilAktivitet(call.ident(), aktivitet)

                            call.respond(HttpStatusCode.Created, aktivitet.toAktivitetDTO())
                        }

                        route("{aktivitetId}") {
                            get {
                                val aktivitet =
                                    rapporteringsperiodeRepository.hentAktivitet(
                                        call.ident(),
                                        call.finnUUID("aktivitetId"),
                                    )
                                call.respond(HttpStatusCode.OK, aktivitet)
                            }

                            delete {
                                rapporteringsperiodeRepository.slettAktivitet(
                                    call.ident(),
                                    call.finnUUID("aktivitetId"),
                                )
                                call.respond(HttpStatusCode.NoContent)
                            }
                        }
                    }
                }
            }
        }
    }
}

private class RapporteringsperiodeMapper(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
    private lateinit var id: UUID
    private lateinit var periode: ClosedRange<LocalDate>
    private lateinit var tilstand: Rapporteringsperiode.TilstandType
    private val dager: SortedSet<Dag> = sortedSetOf(Dag.Companion.eldsteDagFørst)
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
                dager = dager.mapIndexed { index, it -> DagMapper(index, it).dto },
            )
        }

    init {
        rapporteringsperiode.accept(this)
    }

    class AktivitetMapper(aktivitet: Aktivitet) : AktivitetVisitor {
        lateinit var aktivitetDTO: AktivitetDTO

        init {
            aktivitet.accept(this)
        }

        override fun visit(
            aktivitet: Aktivitet,
            dato: LocalDate,
            tid: kotlin.time.Duration,
            type: Aktivitet.AktivitetType,
            uuid: UUID,
        ) {
            aktivitetDTO = AktivitetDTO(
                type = AktivitetTypeDTO.valueOf(type.name),
                dato = dato,
                id = uuid,
                timer = tid.toIsoString(),
            )
        }
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
    }
}

private fun Aktivitet.toAktivitetDTO(): AktivitetDTO {
    val aktivitetType = when (this) {
        is Aktivitet.Arbeid -> AktivitetTypeDTO.Arbeid
        is Aktivitet.Ferie -> AktivitetTypeDTO.Ferie
        is Aktivitet.Syk -> AktivitetTypeDTO.Syk
    }
    return AktivitetDTO(
        type = aktivitetType,
        dato = this.dato,
        id = this.uuid,
        timer = this.tid.toIsoString(),
    )
}
