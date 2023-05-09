package no.nav.dagpenger.rapportering.helpers

import no.nav.dagpenger.rapportering.Person

object TestData {
    val testIdent = "01010125255"
    val testPerson get() = Person(testIdent)
}