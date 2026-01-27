package no.nav.dagpenger.rapportering.model

import no.nav.dagpenger.rapportering.connector.AnsvarligSystem

data class MidlertidigLagretData(
    val ident: String,
    val navn: String,
    val loginLevel: Int,
    val headers: Map<String, List<String>>,
    val rapporteringsperiode: String,
    val ansvarligSystem: AnsvarligSystem,
)
