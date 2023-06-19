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
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.dto.RapporteringsperiodeMapper
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetInputDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.PostRapporteringsperiodeSokRequestDTO
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.ManuellInnsendingHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDate
import java.util.UUID

internal fun Application.rapporteringApi(
    rapporteringsperiodeRepository: RapporteringsperiodeRepository,
    mediator: IHendelseMediator,
) {
    routing {
        authenticate("azureAd") {
            post<PostRapporteringsperiodeSokRequestDTO>("/rapporteringsperioder/sok") {
                val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(it.ident)
                    .map { rapporteringsperiode -> RapporteringsperiodeMapper(rapporteringsperiode).dto }

                call.respond(HttpStatusCode.OK, rapporteringsperioder)
            }
        }
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
                            rapporteringsperiodeRepository.hentRapporteringsperioder(call.ident()).hentGjeldende()
                                ?.let { RapporteringsperiodeMapper(it).dto }
                                ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

                        call.respond(HttpStatusCode.OK, rapporteringsperiode)
                    }
                }

                route("/{periodeId}") {
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
                            mediator.behandle(GodkjennPeriodeHendelse(call.ident(), call.finnUUID("periodeId")))
                            val periode = rapporteringsperiodeRepository.hentRapporteringsperiode(
                                call.ident(),
                                call.finnUUID("periodeId"),
                            )!!.let { RapporteringsperiodeMapper(it).dto }

                            call.respond(HttpStatusCode.Created, periode)
                        }
                    }

                    route("/innsending") {
                        post {
                            mediator.behandle(ManuellInnsendingHendelse(call.ident(), call.finnUUID("periodeId")))
                            val periode = rapporteringsperiodeRepository.hentRapporteringsperiode(
                                call.ident(),
                                call.finnUUID("periodeId"),
                            )!!.let { RapporteringsperiodeMapper(it).dto }

                            call.respond(HttpStatusCode.Created, periode)
                        }
                    }

                    route("/korrigering") {
                        post {
                            mediator.behandle(KorrigerPeriodeHendelse(call.ident(), call.finnUUID("periodeId")))
                            val korrigering =
                                rapporteringsperiodeRepository.hentRapporteringsperiode(
                                    call.ident(),
                                    call.finnUUID("periodeId"),
                                )!!.let {
                                    RapporteringsperiodeMapper(it.finnSisteKorrigering()).dto
                                }

                            call.respond(HttpStatusCode.OK, korrigering)
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
