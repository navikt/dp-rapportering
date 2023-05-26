package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.AktivitetVisitor
import no.nav.dagpenger.rapportering.RapporteringsperiodVisitor
import no.nav.dagpenger.rapportering.Rapporteringsperiode
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
                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                route("{id}") {
                    get {
                        val dto =
                            rapporteringsperiodeRepository.hentRapporteringsperiode(call.ident(), call.finnUUID("id"))
                                ?.let {
                                    RapporteringsPeriodeMapper(it).dto
                                } ?: RapporteringsperiodeDTO(
                                id = UUID.randomUUID(),
                                fraOgMed = LocalDate.of(2023, 5, 22),
                                tilOgMed = LocalDate.of(2023, 6, 4),
                                status = RapporteringsperiodeDTO.Status.TilUtfylling,
                                dager = lagNoe(),
                                aktiviteter = aktivitetRepository.hentAktiviteter(call.ident()).map {
                                    RapporteringsPeriodeMapper.AktivitetMapper(it).aktivitetDTO
                                },
                            )
                        call.respond(HttpStatusCode.OK, dto)
                    }

                    route("/innsending") {
                        post {
                            val id = call.parameters["id"]?.let {
                                UUID.fromString(it)
                            }

                            call.respond(
                                HttpStatusCode.Created,
                                RapporteringsperiodeDTO(
                                    id = UUID.randomUUID(),
                                    fraOgMed = LocalDate.of(2023, 5, 22),
                                    tilOgMed = LocalDate.of(2023, 6, 4),
                                    status = RapporteringsperiodeDTO.Status.TilUtfylling,
                                    dager = lagNoe(),
                                    aktiviteter = listOf(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class RapporteringsPeriodeMapper(rapporteringsperiode: Rapporteringsperiode) : RapporteringsperiodVisitor {
    lateinit var id: UUID
    lateinit var periode: ClosedRange<LocalDate>
    lateinit var tilstand: Rapporteringsperiode.TilstandType
    lateinit var aktiviteter: List<Aktivitet>

    val dto: RapporteringsperiodeDTO
        get() {
            return RapporteringsperiodeDTO(
                id = id,
                fraOgMed = periode.start,
                tilOgMed = periode.endInclusive,
                status = when (tilstand) {
                    Opprettet -> RapporteringsperiodeDTO.Status.TilUtfylling
                    Rapporteringsperiode.TilstandType.Innsendt -> RapporteringsperiodeDTO.Status.Innsendt
                },
                dager = lagNoe(),
                aktiviteter = aktiviteter.tilDto(),
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

        override fun visit(dato: LocalDate, tid: Duration, type: Aktivitet.AktivitetType, uuid: UUID) {
            aktivitetDTO = AktivitetDTO(
                type = when (type) {
                    Aktivitet.AktivitetType.Arbeid -> AktivitetTypeDTO.Arbeid
                    Aktivitet.AktivitetType.Syk -> AktivitetTypeDTO.Syk
                    Aktivitet.AktivitetType.Ferie -> AktivitetTypeDTO.Ferie
                },
                dato = dato,
                id = uuid,
                timer = tid.toDouble(DurationUnit.HOURS).toBigDecimal(),
            )
        }
    }

    private fun List<Aktivitet>.tilDto() = this.map {
        AktivitetMapper(it).aktivitetDTO
    }

    override fun visit(id: UUID, periode: ClosedRange<LocalDate>, tilstand: Rapporteringsperiode.TilstandType) {
        this.id = id
        this.periode = periode
        this.tilstand = tilstand
    }

    override fun visit(aktiviteter: List<Aktivitet>) {
        this.aktiviteter = aktiviteter
    }
}

// todo Dette må fikses på når vi lager "dager" i en Rapporteringsperiode
private fun lagNoe(): List<RapporteringsperiodeDagerInnerDTO> {
    val date = LocalDate.of(2023, 5, 22)

    return (0..13).map { index ->
        RapporteringsperiodeDagerInnerDTO(
            dagIndex = index,
            dato = date.plusDays(index.toLong()),
            muligeAktiviteter = listOf(
                AktivitetTypeDTO.Arbeid,
                AktivitetTypeDTO.Ferie,
                AktivitetTypeDTO.Syk,
            ).shuffled().subList(0, 2),
        )
    }
}
