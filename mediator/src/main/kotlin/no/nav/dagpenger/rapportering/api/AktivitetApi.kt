package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.models.Aktivitet
import no.nav.dagpenger.rapportering.api.models.AktivitetInput
import no.nav.dagpenger.rapportering.api.models.AktivitetType
import java.time.LocalDate
import java.util.UUID

fun Application.aktivitetApi() {
    routing {
        route("/aktivitet") {
            get {
                val aktiviteter = listOf<Aktivitet>()
                call.respond(HttpStatusCode.OK, aktiviteter)
            }
            post {
                val aktivitetInput = call.receive<AktivitetInput>()

                val aktivitet = Aktivitet(
                    type = aktivitetInput.type,
                    dato = aktivitetInput.dato ?: LocalDate.now(), // TODO
                    id = UUID.randomUUID(),
                    timer = aktivitetInput.timer?.toBigDecimal(),
                )

                call.respond(HttpStatusCode.Created, aktivitet)
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
                        timer = aktivitetInput.timer?.toBigDecimal(),
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
