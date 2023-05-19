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
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetInput
import java.time.LocalDate
import java.util.UUID

fun Application.aktivitetApi() {
    routing {
        route("/aktivitet") {
            get {
                val aktiviteter = listOf<AktivitetDTO>()
                call.respond(HttpStatusCode.OK, aktiviteter)
            }

            post {
                val aktivitetInput = call.receive<AktivitetInput>()

                val aktivitetDTO = AktivitetDTO(
                    type = AktivitetDTO.Type.ARBEID, // TODO: AktivitetDTO.Type.valueOf(aktivitetInput.type)
                    dato = aktivitetInput.dato ?: LocalDate.now(), // TODO
                    id = UUID.randomUUID().toString(),
                    timer = aktivitetInput.timer?.toBigDecimal(),
                )

                call.respond(HttpStatusCode.Created, aktivitetDTO)
            }

            route("{id}") {
                get {
                    val aktivitet = AktivitetDTO(type = AktivitetDTO.Type.FERIE, dato = LocalDate.now())
                    call.respond(HttpStatusCode.OK, aktivitet)
                }

                put {
                    val aktivitetInput = call.receive<AktivitetInput>()

                    val aktivitetDTO = AktivitetDTO(
                        type = AktivitetDTO.Type.ARBEID, // TODO: AktivitetDTO.Type.valueOf(aktivitetInput.type)
                        dato = aktivitetInput.dato ?: LocalDate.now(), // TODO
                        id = UUID.randomUUID().toString(),
                        timer = aktivitetInput.timer?.toBigDecimal(),
                    )

                    call.respond(HttpStatusCode.OK, aktivitetDTO)
                }

                delete {
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
