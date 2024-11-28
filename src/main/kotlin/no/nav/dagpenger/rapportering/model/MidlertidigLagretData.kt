package no.nav.dagpenger.rapportering.model

data class MidlertidigLagretData(
    val ident: String,
    val navn: String,
    val loginLevel: Int,
    val headers: Map<String, List<String>>,
    val rapporteringsperiode: String,
)
