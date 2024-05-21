package no.nav.dagpenger.rapportering.model

class Person(
    val ident: String,
    val rapporteringsperiode: List<Rapporteringsperiode>,
    val registrertArbeidssøker: Boolean,
)
