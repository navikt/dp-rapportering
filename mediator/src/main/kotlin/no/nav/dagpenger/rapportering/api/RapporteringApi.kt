package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import no.nav.dagpenger.rapportering.api.models.AktivitetType
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode.Status.TIL_UTFYLLING
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInner
import java.time.LocalDate
import java.util.UUID

fun Application.rapporteringApi() {
    routing {
        route("/rapporteringsperioder") {
            get {
                val rapporteringsperioder = listOf<Rapporteringsperiode>()
                call.respond(HttpStatusCode.OK, rapporteringsperioder)
            }

            route("{id}") {
                get {
                    val rapporteringsperiode = Rapporteringsperiode(
                        id = UUID.randomUUID(),
                        fraOgMed = LocalDate.of(2023, 5, 22),
                        tilOgMed = LocalDate.of(2023, 6, 4),
                        status = TIL_UTFYLLING,
                        dager = lagNoe(),
                        aktiviteter = listOf(),
                    )
                    call.respond(HttpStatusCode.OK, rapporteringsperiode)
                }

                route("/innsending") {
                    post {
                        val id = call.parameters["id"]?.let {
                            UUID.fromString(it)
                        }

                        call.respond(
                            HttpStatusCode.Created,
                            Rapporteringsperiode(
                                id = UUID.randomUUID(),
                                fraOgMed = LocalDate.of(2023, 5, 22),
                                tilOgMed = LocalDate.of(2023, 6, 4),
                                status = TIL_UTFYLLING,
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

private fun lagNoe(): List<RapporteringsperiodeDagerInner> {
    val date = LocalDate.of(2023, 5, 22)

    return (0..13).map { index ->
        RapporteringsperiodeDagerInner(
            dagIndex = index,
            dato = date.plusDays(index.toLong()),
            muligeAktiviteter = listOf(
                AktivitetType.ARBEID,
                AktivitetType.FERIE,
                AktivitetType.SYK,
            ),
        )
    }
}
