package no.nav.dagpenger.rapportering.model

data class Person(
    val personId: Long,
    val etternavn: String,
    val fornavn: String,
    val maalformkode: String,
    val meldeform: String,
)
