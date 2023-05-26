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
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.models.AktivitetType
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode
import no.nav.dagpenger.rapportering.api.models.Rapporteringsperiode.Status.TilUtfylling
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeDagerInner
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import java.time.LocalDate
import java.util.UUID

fun Application.rapporteringApi(rapporteringsperiodeRepository: RapporteringsperiodeRepository) {
    routing {
        authenticate("tokenX") {
            route("/rapporteringsperioder") {
                get {
                    val rapporteringsperioder = rapporteringsperiodeRepository.hentRapporteringsperioder(call.ident())
                    call.respond(HttpStatusCode.OK, rapporteringsperioder)
                }

                route("{id}") {
                    get {
                        val rapporteringsperiode =
                            rapporteringsperiodeRepository.hentRapporteringsperiode(call.ident(), call.finnUUID("id"))
                                ?: Rapporteringsperiode(
                                    id = UUID.randomUUID(),
                                    fraOgMed = LocalDate.of(2023, 5, 22),
                                    tilOgMed = LocalDate.of(2023, 6, 4),
                                    status = TilUtfylling,
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
                                    status = TilUtfylling,
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

private fun lagNoe(): List<RapporteringsperiodeDagerInner> {
    val date = LocalDate.of(2023, 5, 22)

    return (0..13).map { index ->
        RapporteringsperiodeDagerInner(
            dagIndex = index,
            dato = date.plusDays(index.toLong()),
            muligeAktiviteter = listOf(
                AktivitetType.Arbeid,
                AktivitetType.Ferie,
                AktivitetType.Syk,
            ).shuffled().subList(0, 2),
        )
    }
}
