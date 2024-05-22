package no.nav.dagpenger.rapportering.model

data class Person(
    val ident: String,
    val rapporteringsperiode: List<Rapporteringsperiode>,
    val registrertArbeidssøker: Boolean,
)
