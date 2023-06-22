package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.azureAdIssuer
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.tokenXIssuer
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.dto.RapporteringsperiodeMapper
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetNyDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeNyDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeSokDTO
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.ManuellInnsendingHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import java.time.LocalDateTime
import java.util.UUID

internal fun Application.rapporteringApi(
    rapporteringsperiodeRepository: RapporteringsperiodeRepository,
    mediator: IHendelseMediator,
    tilgangskontroll: Tilgangskontroll = RapporteringsperiodeTilgangskontroll(rapporteringsperiodeRepository),
) {
    routing {
        swaggerUI(path = "openapi", swaggerFile = "rapportering-api.yaml")

        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val rapporteringsperioder = rapporteringsperiodeRepository
                        .hentRapporteringsperioder(call.ident())
                        .map { RapporteringsperiodeMapper(it).dto }

                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
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
            }
        }

        authenticate("azureAd") {
            route("/rapporteringsperioder/") {
                post<RapporteringsperiodeSokDTO>("/sok") {
                    val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(it.ident)
                        .map { rapporteringsperiode -> RapporteringsperiodeMapper(rapporteringsperiode).dto }

                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                post<RapporteringsperiodeNyDTO> {
                    val fom = it.fraOgMed?.let { fraOgMed -> fraOgMed.atStartOfDay() } ?: LocalDateTime.now()
                    val harGjeldende = rapporteringsperiodeRepository
                        .hentRapporteringsperiodeFor(it.ident, fom.toLocalDate())
                        ?.let { true } ?: false
                    if (harGjeldende) call.respond(HttpStatusCode.Conflict)

                    mediator.behandle(SøknadInnsendtHendelse(UUID.randomUUID(), ident = it.ident, fom))
                    mediator.behandle(RapporteringspliktDatoHendelse(UUID.randomUUID(), it.ident, fom, fom.toLocalDate(), fom.toLocalDate()))

                    call.respond(HttpStatusCode.Created)
                }
            }
        }

        authenticate("tokenX", "azureAd") {
            route("/rapporteringsperioder/") {
                route("/{periodeId}") {
                    get {
                        val ident = tilgangskontroll.verifiserTilgang(call)
                        val periodeId = call.finnUUID("periodeId")
                        val dto = rapporteringsperiodeRepository
                            .hentRapporteringsperiode(ident, periodeId)
                            ?.let { RapporteringsperiodeMapper(it, periodeId).dto }
                            ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

                        call.respond(HttpStatusCode.OK, dto)
                    }

                    route("/godkjenn") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val periodeId = call.finnUUID("periodeId")

                            mediator.behandle(GodkjennPeriodeHendelse(ident, periodeId))
                            val periode = rapporteringsperiodeRepository
                                .hentRapporteringsperiode(ident, periodeId)!!
                                .let { RapporteringsperiodeMapper(it, periodeId).dto }

                            call.respond(HttpStatusCode.Created, periode)
                        }
                    }

                    route("/innsending") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val periodeId = call.finnUUID("periodeId")

                            mediator.behandle(ManuellInnsendingHendelse(ident, periodeId))
                            val periode = rapporteringsperiodeRepository
                                .hentRapporteringsperiode(ident, periodeId)!!
                                .let { RapporteringsperiodeMapper(it, periodeId).dto }

                            call.respond(HttpStatusCode.Created, periode)
                        }
                    }

                    route("/korrigering") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val periodeId = call.finnUUID("periodeId")
                            mediator.behandle(KorrigerPeriodeHendelse(ident, periodeId))
                            val korrigering = rapporteringsperiodeRepository
                                .hentRapporteringsperiode(ident, periodeId)!!
                                .let { RapporteringsperiodeMapper(it.finnSisteKorrigering()).dto }

                            call.respond(HttpStatusCode.OK, korrigering)
                        }
                    }

                    route("/aktivitet") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val aktivitetInput = call.receive<AktivitetNyDTO>()
                            val aktivitet = aktivitetInput.toAktivitet()
                            val periodeId = call.finnUUID("periodeId")

                            mediator.behandle(NyAktivitetHendelse(ident, periodeId, aktivitet))

                            call.respond(HttpStatusCode.Created, aktivitet.toAktivitetDTO())
                        }

                        route("{aktivitetId}") {
                            delete {
                                val ident = tilgangskontroll.verifiserTilgang(call)
                                mediator.behandle(
                                    SlettAktivitetHendelse(
                                        ident,
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

private class RapporteringsperiodeTilgangskontroll(private val repository: RapporteringsperiodeRepository) :
    Tilgangskontroll {
    override fun verifiserTilgang(call: ApplicationCall): String {
        val periodeId = call.finnUUID("periodeId")
        val ident = repository.finnIdentForPeriode(periodeId)
            ?: throw NotFoundException("Rapporteringsperioden finnes ikke")

        return when (call.authentication.principal<JWTPrincipal>()?.payload?.issuer) {
            tokenXIssuer -> {
                if (call.ident() != ident) throw IkkeTilgangException("Ikke tilgang")
                call.ident()
            }

            azureAdIssuer -> ident
            else -> throw IkkeTilgangException("Ikke tilgang")
        }
    }
}

private class IkkeTilgangException(message: String) : Exception(message)

private fun AktivitetNyDTO.toAktivitet() =
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
