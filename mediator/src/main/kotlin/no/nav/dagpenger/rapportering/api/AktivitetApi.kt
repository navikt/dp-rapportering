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
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.Aktivitet.Arbeid
import no.nav.dagpenger.rapportering.Aktivitet.Ferie
import no.nav.dagpenger.rapportering.Aktivitet.Syk
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.api.models.Aktivitet
import no.nav.dagpenger.rapportering.api.models.AktivitetInput
import no.nav.dagpenger.rapportering.api.models.AktivitetType
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import java.time.LocalDate
import java.util.UUID

internal fun Application.aktivitetApi(mediator: IHendelseMediator) {
    routing {
        authenticate("tokenX") {
            route("/aktivitet") {
                get {
                    val aktiviteter = listOf<Aktivitet>()
                    call.respond(HttpStatusCode.OK, aktiviteter)
                }
                post {
                    val aktivitetInput = call.receive<AktivitetInput>()

                    val aktivitet: no.nav.dagpenger.rapportering.Aktivitet = when (aktivitetInput.type) {
                        AktivitetType.ARBEID -> Arbeid(
                            dato = aktivitetInput.dato,
                            arbeidstimer = aktivitetInput.timer
                                ?: throw IllegalArgumentException("Må ha antall arbeidstimer"),
                        )

                        AktivitetType.SYK -> Syk(
                            dato = aktivitetInput.dato,
                        )

                        AktivitetType.FERIE -> Ferie(
                            dato = aktivitetInput.dato,
                        )
                    }

                    mediator.behandle(
                        NyAktivitetHendelse(
                            meldingsreferanseId = UUID.randomUUID(),
                            ident = "",
                            aktivitet,
                        ),
                    )

                    call.respond(HttpStatusCode.Created, aktivitet.toAktivitetDTO())
                }

                route("{id}") {
                    get {
                        val aktivitet = Aktivitet(type = AktivitetType.ARBEID, dato = LocalDate.now())
                        call.respond(HttpStatusCode.OK, aktivitet)
                    }

                    put {
                        val aktivitetInput = call.receive<AktivitetInput>()

                        val aktivitet = Aktivitet(
                            type = aktivitetInput.type,
                            dato = aktivitetInput.dato ?: LocalDate.now(), // TODO
                            id = UUID.randomUUID(),
                            timer = aktivitetInput.timer,
                        )

                        call.respond(HttpStatusCode.OK, aktivitet)
                    }

                    delete {
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }
        }
    }
}

internal fun no.nav.dagpenger.rapportering.Aktivitet.toAktivitetDTO(): Aktivitet {
    val aktivitetType = when (this) {
        is Arbeid -> AktivitetType.ARBEID
        is Ferie -> AktivitetType.FERIE
        is Syk -> AktivitetType.SYK
    }
    return Aktivitet(
        type = aktivitetType,
        dato = this.dato,
        id = this.uuid,
        timer = this.antall.inWholeHours.toBigDecimal(),
    )
}
