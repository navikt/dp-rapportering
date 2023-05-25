package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

class InMemoryPersonRepository : PersonRepository {
    private val personer: MutableSet<Person> = mutableSetOf()

    override fun hentPerson(ident: String): Person? {
        return personer.singleOrNull { it.ident == ident }
    }
}
