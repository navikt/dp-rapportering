package no.nav.dagpenger.rapportering.model

import io.ktor.http.Headers

data class MidlertidigLagretData(
    val ident: String,
    val navn: String,
    val headers: Headers,
    val rapporteringsperiode: Rapporteringsperiode,
)
