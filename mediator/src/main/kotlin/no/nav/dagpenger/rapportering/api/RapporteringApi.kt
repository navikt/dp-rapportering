package no.nav.dagpenger.rapportering.api

import io.ktor.http.HttpHeaders
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
import no.nav.dagpenger.rapportering.Godkjenningsendring
import no.nav.dagpenger.rapportering.IHendelseMediator
import no.nav.dagpenger.rapportering.Rapporteringsperiode.Companion.hentGjeldende
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.Issuer.AzureAD
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.Issuer.TokenX
import no.nav.dagpenger.rapportering.api.auth.ident
import no.nav.dagpenger.rapportering.api.auth.issuer
import no.nav.dagpenger.rapportering.api.auth.saksbehandlerId
import no.nav.dagpenger.rapportering.api.dto.RapporteringsperiodeMapper
import no.nav.dagpenger.rapportering.api.models.AktivitetDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetNyDTO
import no.nav.dagpenger.rapportering.api.models.AktivitetTypeDTO
import no.nav.dagpenger.rapportering.api.models.FrontendDataDTO
import no.nav.dagpenger.rapportering.api.models.GodkjennNyDTO
import no.nav.dagpenger.rapportering.api.models.ProblemDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeNyDTO
import no.nav.dagpenger.rapportering.api.models.RapporteringsperiodeSokDTO
import no.nav.dagpenger.rapportering.hendelser.AvgodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.GodkjennPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.KorrigerPeriodeHendelse
import no.nav.dagpenger.rapportering.hendelser.NyAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.RapporteringspliktDatoHendelse
import no.nav.dagpenger.rapportering.hendelser.SlettAktivitetHendelse
import no.nav.dagpenger.rapportering.hendelser.SøknadInnsendtHendelse
import no.nav.dagpenger.rapportering.repository.RapporteringsperiodeRepository
import no.nav.dagpenger.rapportering.strategiForBeregningsdato
import no.nav.dagpenger.rapportering.tidslinje.Aktivitet
import no.nav.helse.rapids_rivers.JsonMessage
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.io.ByteArrayOutputStream
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

                        when (rapporteringsperiode) {
                            null -> call.respond(HttpStatusCode.NotFound)
                            else -> call.respond(HttpStatusCode.OK, rapporteringsperiode)
                        }
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
                // TODO: Endepunkt for å manuelt opprette rapporteringsplikt. Vi burde nok inneføre en egen hendelse istedenfor SøknadInnsendt + RapporteringspliktDato
                post<RapporteringsperiodeNyDTO> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ProblemDTO(
                            title = "Ikke implementert",
                            detail = "Ikke bruk denne :)",
                            status = HttpStatusCode.Forbidden.value,
                        ),
                    )
                    return@post
                    val fom = it.fraOgMed?.let { fraOgMed -> fraOgMed.atStartOfDay() } ?: LocalDateTime.now()
                    val harGjeldende = rapporteringsperiodeRepository
                        .hentRapporteringsperiodeFor(it.ident, fom.toLocalDate())
                        ?.let { true } ?: false
                    if (harGjeldende) call.respond(HttpStatusCode.Conflict)

                    mediator.behandle(
                        SøknadInnsendtHendelse(
                            UUID.randomUUID(),
                            ident = it.ident,
                            fom,
                            søknadId = UUID.randomUUID(),
                        ),
                    )
                    mediator.behandle(
                        RapporteringspliktDatoHendelse(
                            UUID.randomUUID(),
                            it.ident,
                            fom,
                            fom.toLocalDate(),
                            strategiForBeregningsdato,
                        ),
                    )

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
                            val hendelse = when (call.issuer()) {
                                AzureAD -> {
                                    val godkjenning = call.receive<GodkjennNyDTO>()
                                    require(godkjenning.begrunnelse.isNotEmpty()) { "Saksbehandler må oppgi begrunnelse" }

                                    GodkjennPeriodeHendelse(
                                        ident,
                                        periodeId,
                                        Godkjenningsendring.Saksbehandler(call.saksbehandlerId()),
                                        godkjenning.begrunnelse,
                                    )
                                }

                                TokenX -> {
                                    val dto = call.receive<FrontendDataDTO>()

                                    val data = mapOf(
                                        "timestamp" to LocalDateTime.now(),
                                        "claims" to call.authentication.principal<JWTPrincipal>()?.payload?.claims.orEmpty(),
                                        "image" to dto.image,
                                        "kildekode" to dto.commit,
                                        "klient" to call.request.headers[HttpHeaders.UserAgent].orEmpty(),
                                        "språk" to "no-NB",
                                    )
                                    val json = JsonMessage.newMessage(data).toJson()
                                    val pdf = opprettPdf(data)
                                    // Journalføre

                                    GodkjennPeriodeHendelse(ident, periodeId)
                                }
                            }

                            mediator.behandle(hendelse)

                            call.respond(HttpStatusCode.OK, hendelse.godkjenningsendring)
                        }
                    }

                    route("/avgodkjenn") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val periodeId = call.finnUUID("periodeId")

                            val hendelse = when (call.issuer()) {
                                AzureAD -> {
                                    val avgodkjenning = call.receive<GodkjennNyDTO>()
                                    require(avgodkjenning.begrunnelse.isNotEmpty()) { "Saksbehandler må oppgi begrunnelse" }

                                    AvgodkjennPeriodeHendelse(
                                        ident,
                                        periodeId,
                                        Godkjenningsendring.Saksbehandler(call.saksbehandlerId()),
                                        avgodkjenning.begrunnelse,
                                    )
                                }

                                TokenX -> AvgodkjennPeriodeHendelse(ident, periodeId)
                            }

                            mediator.behandle(hendelse)
                            call.respond(HttpStatusCode.OK, hendelse.godkjenningsendring)
                        }
                    }

                    route("/korrigering") {
                        post {
                            val ident = tilgangskontroll.verifiserTilgang(call)
                            val periodeId = call.finnUUID("periodeId")

                            mediator.behandle(KorrigerPeriodeHendelse(ident, periodeId))
                            val korrigering = rapporteringsperiodeRepository
                                .hentRapporteringsperiode(ident, periodeId)!!
                                .let {
                                    RapporteringsperiodeMapper(
                                        it,
                                        it.finnSisteKorrigering().rapporteringsperiodeId,
                                    ).dto
                                }

                            call.respond(HttpStatusCode.Created, korrigering)
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

        return when (call.issuer()) {
            TokenX -> {
                if (call.ident() != ident) throw IkkeTilgangException("Ikke tilgang")
                call.ident()
            }

            AzureAD -> ident
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

private fun opprettPdf(data: Map<String, Any>): ByteArray {
    var output: ByteArray

    PDDocument().use { document ->
        val font = PDType0Font.load(
            document,
            {}::class.java.getResource("/SourceSansPro-Regular.ttf")!!.openStream(),
        )

        val page = PDPage(PDRectangle.A4)
        document.addPage(page)
        PDPageContentStream(document, page).use { contentStream ->
            contentStream.beginText()
            contentStream.setFont(font, 12f)
            contentStream.setLeading(14.5f)
            contentStream.newLineAtOffset(25f, 800f)

            iterate(data, contentStream, "")

            contentStream.endText()
        }

        ByteArrayOutputStream().use {
            document.save(it)
            output = it.toByteArray()
        }
    }

    return output
}

private fun iterate(map: Map<String, Any>, contentStream: PDPageContentStream, indent: String) {
    map.forEach { element ->
        if (element.value is Map<*, *>) {
            contentStream.showText(element.key + ": {")
            contentStream.newLine()
            iterate(element.value as Map<String, Any>, contentStream, "$indent    ")
            contentStream.showText("}")
            contentStream.newLine()
        } else {
            contentStream.showText(indent + element.key + ": " + element.value)
            contentStream.newLine()
        }
    }
}
