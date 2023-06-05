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
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Godkjent
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.Innsendt
import no.nav.dagpenger.rapportering.Rapporteringsperiode.TilstandType.TilUtfylling
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetInputDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInnerDTO
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Dag
import java.time.LocalDate
import java.util.SortedSet
import java.util.UUID
import kotlin.time.Duration

internal fun Application.rapporteringApi(
    rapporteringsperiodeRepository: RapporteringsperiodeRepository,
    mediator: IHendelseMediator,
) {
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(call.ident())
                        .map { RapporteringsperiodeMapper(it).dto }

                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                post {
                    // TODO: Fjern eller legg på tokenx-auth (så bare saksbehandler kan. Det bør sannsynligvis helst gå via Kafka for sporing)
                    val harGjeldende =
                        rapporteringsperiodeRepository.hentRapporteringsperiodeFor(call.ident(), LocalDate.now())
                            ?.let { true } ?: false
                    if (harGjeldende) call.respond(HttpStatusCode.Conflict)

                    mediator.behandle(SøknadInnsendtHendelse(UUID.randomUUID(), ident = call.ident()))

                    call.respond(HttpStatusCode.Created)
                }

                route("/gjeldende") {
                    get {
                        val rapporteringsperiode =
                            rapporteringsperiodeRepository.hentRapporteringsperiodeFor(call.ident(), LocalDate.now())
                                ?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

                        call.respond(HttpStatusCode.OK, rapporteringsperiode)
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
                        post {
                            val aktivitetInput = call.receive<AktivitetInputDTO>()
                            val aktivitet = aktivitetInput.toAktivitet()
                            val periodeId = call.finnUUID("periodeId")

                            mediator.behandle(NyAktivitetHendelse(call.ident(), periodeId, aktivitet))

                            call.respond(HttpStatusCode.Created, aktivitet.toAktivitetDTO())
                        }

                        route("{aktivitetId}") {
                            delete {
                                mediator.behandle(
                                    SlettAktivitetHendelse(
                                        call.ident(),
                                        call.finnUUID("periodeId"),
                                        call.finnUUID("aktivitetId"),
                                    ),
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

private fun AktivitetInputDTO.toAktivitet() =
    when (type) {
        AktivitetTypeDTO.Arbeid -> Aktivitet.Arbeid(
            dato = dato,
            arbeidstimer = timer ?: throw IllegalArgumentException("Må ha antall arbeidstimer"),
        )

        AktivitetTypeDTO.Syk -> Aktivitet.Syk(
            dato = dato,
        )

        AktivitetTypeDTO.Ferie -> Aktivitet.Ferie(
            dato = dato,
        )
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
                    TilUtfylling -> RapporteringsperiodeDTO.Status.TilUtfylling
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

    override fun visit(
        rapporteringsperiode: Rapporteringsperiode,
        id: UUID,
        periode: ClosedRange<LocalDate>,
        tilstand: Rapporteringsperiode.TilstandType,
        rapporteringsfrist: LocalDate,
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
