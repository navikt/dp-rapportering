package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

interface PersonRepository {
    fun hentEllerOpprettPerson(ident: String): Person
    fun lagre(person: Person)
    fun hentIdenterMedGodkjentPeriode(): List<String>
}
