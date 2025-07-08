package no.nav.dagpenger.rapportering.api.auth

import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTCredential
import io.ktor.server.auth.jwt.JWTPayloadHolder
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest
import no.nav.dagpenger.rapportering.api.auth.AuthFactory.issuerFromString

internal fun validator(jwtCredential: JWTCredential): JWTPrincipal {
    requirePid(jwtCredential)
    return JWTPrincipal(jwtCredential.payload)
}

internal fun ApplicationCall.ident(): String = requireNotNull(this.authentication.principal<JWTPrincipal>()) { "Ikke autentisert" }.fnr

internal val JWTPrincipal.fnr get(): String = requirePid(this)

internal fun ApplicationCall.saksbehandlerId() =
    requireNotNull(this.authentication.principal<JWTPrincipal>()) { "Ikke autentisert" }.saksbehandlerId()

private fun requirePid(credential: JWTPayloadHolder): String =
    requireNotNull(credential.payload.claims["pid"]?.asString()) { "Token må inneholde fødselsnummer for personen i claim 'pid'" }

private fun JWTPrincipal.saksbehandlerId(): String = requireNotNull(this.payload.claims["NAVident"]?.asString())

private fun JWTPrincipal.saksbehandlerApp(): String = requireNotNull(this.payload.claims["azp_name"]?.asString())

private fun JWTPrincipal.saksbehandlerEpostAdresse(): String = requireNotNull(this.payload.claims["preferred_username"]?.asString())

internal fun ApplicationRequest.jwt(): String =
    this.parseAuthorizationHeader().let { authHeader ->
        (authHeader as? HttpAuthHeader.Single)?.blob ?: throw IllegalArgumentException("JWT not found")
    }

internal fun ApplicationCall.optionalIdent(): String? =
    requireNotNull(this.authentication.principal<JWTPrincipal>()).payload.claims["pid"]?.asString()

internal fun ApplicationCall.issuer() =
    issuerFromString(
        this.authentication
            .principal<JWTPrincipal>()
            ?.payload
            ?.issuer,
    )

internal fun ApplicationCall.loginLevel(): Int {
    val jwt = requireNotNull(this.authentication.principal<JWTPrincipal>()) { "Ikke autentisert" }
    val acrClaim = requireNotNull(jwt.payload.claims["acr"]?.asString()) { "Kan ikke finne påloggingsnivå" }

    return when (acrClaim) {
        "idporten-loa-substantial", "Level3" -> {
            3
        }

        "idporten-loa-high", "Level4" -> {
            4
        }

        else -> 0
    }
}
