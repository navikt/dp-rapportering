package no.nav.dagpenger.rapportering.repository

import no.nav.dagpenger.rapportering.Person

class InMemoryPersonRepository : PersonRepository {
    private val personer: MutableSet<Person> = mutableSetOf()

    override fun hentPerson(ident: String) = personer.singleOrNull { it.ident == ident }

    override fun hentEllerOpprettPerson(ident: String) = hentPerson(ident) ?: Person(ident).also {
        personer.add(it)
    }
}
