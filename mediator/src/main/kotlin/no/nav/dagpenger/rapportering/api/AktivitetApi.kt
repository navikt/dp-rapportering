package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetInputDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.repository.AktivitetRepository
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet.Syk

internal fun Application.aktivitetApi(repository: AktivitetRepository) {
    routing {
        authenticate("tokenX") {
            route("/aktivitet") {
                get {
                    val aktiviteter = repository.hentAktiviteter(call.ident()).map {
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
                        AktivitetTypeDTO.Arbeid -> Arbeid(
                            dato = aktivitetInput.dato,
                            arbeidstimer = aktivitetInput.timer?.toDouble()
                                ?: throw IllegalArgumentException("Må ha antall arbeidstimer"),
                        )

                        AktivitetTypeDTO.Syk -> Syk(
                            dato = aktivitetInput.dato,
                        )

                        AktivitetTypeDTO.Ferie -> Ferie(
                            dato = aktivitetInput.dato,
                        )
                    }

                    repository.leggTilAktivitet(call.ident(), aktivitet)

                    call.respond(HttpStatusCode.Created, aktivitet.toAktivitetDTO())
                }

                route("{id}") {
                    get {
                        val aktivitet = repository.hentAktivitet(call.ident(), call.finnUUID("id"))
                        call.respond(HttpStatusCode.OK, aktivitet)
                    }

                    delete {
                        repository.slettAktivitet(call.ident(), call.finnUUID("id"))
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

internal fun Aktivitet.toAktivitetDTO(): AktivitetDTO {
    val aktivitetType = when (this) {
        is Arbeid -> AktivitetTypeDTO.Arbeid
        is Ferie -> AktivitetTypeDTO.Ferie
        is Syk -> AktivitetTypeDTO.Syk
    }
    return AktivitetDTO(
        type = aktivitetType,
        dato = this.dato,
        id = this.uuid,
        timer = this.tid.toIsoString(),
    )
}
